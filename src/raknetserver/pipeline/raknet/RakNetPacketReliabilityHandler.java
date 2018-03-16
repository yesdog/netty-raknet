package raknetserver.pipeline.raknet;

import java.util.HashMap;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.raknet.RakNetEncapsulatedData;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetReliability.REntry;
import raknetserver.packet.raknet.RakNetReliability.RakNetACK;
import raknetserver.packet.raknet.RakNetReliability.RakNetNACK;
import raknetserver.utils.Constants;
import raknetserver.utils.PacketHandlerRegistry;
import raknetserver.utils.UINT;

public class RakNetPacketReliabilityHandler extends ChannelDuplexHandler {

	protected static final PacketHandlerRegistry<RakNetPacketReliabilityHandler, RakNetPacket> registry = new PacketHandlerRegistry<>();
	static {
		registry.register(RakNetEncapsulatedData.class, (ctx, handler, packet) -> handler.handleEncapsulatedData(ctx, packet));
		registry.register(RakNetACK.class, (ctx, handler, packet) -> handler.handleAck(ctx, packet));
		registry.register(RakNetNACK.class, (ctx, handler, packet) -> handler.handleNack(ctx, packet));
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof RakNetPacket) {
			registry.handle(ctx, this, (RakNetPacket) msg);
		} else {
			ctx.fireChannelRead(msg);
		}
	}

	protected static final int HALF_WINDOW = UINT.B3.MAX_VALUE / 2;

	protected final HashMap<Integer, RakNetEncapsulatedData> sentPackets = new HashMap<>();
	protected int lastReceivedSeqId = -1;

	protected void handleEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		RakNetEncapsulatedData edata = packet;
		int packetSeqId = edata.getSeqId();
		int seqIdDiff = UINT.B3.minus(packetSeqId, lastReceivedSeqId);
		//ignore duplicate packet
		if ((seqIdDiff == 0) || (seqIdDiff > HALF_WINDOW)) {
			return;
		}
		//send nack for missed packets
		if (seqIdDiff > 1) {
			ctx.writeAndFlush(new RakNetNACK(UINT.B3.plus(lastReceivedSeqId, 1), UINT.B3.minus(packetSeqId, 1))).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
		//can now update last received seq id
		lastReceivedSeqId = packetSeqId;
		//send ack for received packet
		ctx.writeAndFlush(new RakNetACK(packetSeqId)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		//read encapsulated packets
		edata.getPackets().forEach(ctx::fireChannelRead);
	}

	protected void handleAck(ChannelHandlerContext ctx, RakNetACK ack) {
		for (REntry entry : ack.getEntries()) {
			int idStart = entry.idStart;
			int idFinish = entry.idFinish;
			int idDiff = UINT.B3.minus(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (ack confirm range)");
			}
			for (int i = 0; i <= idDiff; i++) {
				sentPackets.remove(UINT.B3.plus(idStart, i));
			}
		}
	}

	protected void handleNack(ChannelHandlerContext ctx, RakNetNACK nack) {
		for (REntry entry : nack.getEntries()) {
			int idStart = entry.idStart;
			int idFinish = entry.idFinish;
			int idDiff = UINT.B3.minus(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (nack resend range)");
			}
			for (int i = 0; i <= idDiff; i++) {
				RakNetEncapsulatedData packet = sentPackets.remove(UINT.B3.plus(idStart, i));
				if (packet != null) {
					sendPacket(ctx, packet, null);
				}
			}
		}
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof EncapsulatedPacket) {
			if (sentPackets.size() > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (unconfirmed sent packets)");
			}
			sendPacket(ctx, new RakNetEncapsulatedData((EncapsulatedPacket) msg), promise);
		} else {
			ctx.writeAndFlush(msg, promise);
		}
	}

	protected int nextSendSeqId = 0;
	protected void sendPacket(ChannelHandlerContext ctx, RakNetEncapsulatedData packet, ChannelPromise promise) {
		int seqId = nextSendSeqId;
		nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
		packet.setSeqId(seqId);
		sentPackets.put(packet.getSeqId(), packet);
		if (promise != null) {
			ctx.writeAndFlush(packet, promise);
		} else {
			ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

}
