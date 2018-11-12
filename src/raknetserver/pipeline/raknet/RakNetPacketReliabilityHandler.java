package raknetserver.pipeline.raknet;

import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.raknet.RakNetEncapsulatedData;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetReliability.REntry;
import raknetserver.packet.raknet.RakNetReliability.RakNetACK;
import raknetserver.packet.raknet.RakNetReliability.RakNetNACK;
import raknetserver.utils.Constants;
import raknetserver.utils.PacketHandlerRegistry;
import raknetserver.utils.UINT;

import java.util.concurrent.TimeUnit;

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

	protected final Int2ObjectOpenHashMap<Object> nackMap = new Int2ObjectOpenHashMap<>();
	protected final Int2ObjectOpenHashMap<Object> ackMap = new Int2ObjectOpenHashMap<>();
	protected final Int2ObjectOpenHashMap<RakNetEncapsulatedData> recvWindow = new Int2ObjectOpenHashMap<>();
	protected final Int2ObjectOpenHashMap<RakNetEncapsulatedData> sentPackets = new Int2ObjectOpenHashMap	<>();
	protected int lastReceivedSeqId = -1;
	protected long lastFlush = 0l;

	protected void processEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		int packetSeqId = packet.getSeqId();
		//can now update last received seq id
		lastReceivedSeqId = packetSeqId;
		//read encapsulated packets
		packet.getPackets().forEach(ctx::fireChannelRead);
		recvWindow.remove(packetSeqId);
	}

	protected void handleEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		int packetSeqId = packet.getSeqId();
		int seqIdDiff = UINT.B3.minus(packetSeqId, lastReceivedSeqId);
		//ignore duplicate packet
		if (seqIdDiff <= 0) { // can be negative?
			return;
		}
		if (seqIdDiff > HALF_WINDOW) {
			throw new DecoderException("Too big packet loss from client");
		}

		ackMap.put(packetSeqId, null);
		nackMap.remove(packetSeqId);

		//packet not needed yet
		if (seqIdDiff > 1) {
			// check missing id range and add to nack map
			for (int i = 1 ; i < seqIdDiff ; i++) {
				int nextId = UINT.B3.plus(lastReceivedSeqId, i);
				if (!recvWindow.containsKey(nextId))
					nackMap.put(nextId, null);
			}
			recvWindow.put(packetSeqId, packet);
		} else {
			//packet needed now
			processEncapsulatedData(ctx, packet);
			//restore any cached sequence available
			for (int nextId = UINT.B3.plus(packetSeqId, 1); recvWindow.containsKey(nextId); nextId = UINT.B3.plus(nextId, 1)) {
				processEncapsulatedData(ctx, recvWindow.get(nextId));
			}
		}
		flushControlResponses(ctx);
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

	protected void flushControlResponses(ChannelHandlerContext ctx) {
		long d = System.currentTimeMillis() - lastFlush;
		if (d < 100) return;

		lastFlush = System.currentTimeMillis();

		//TODO: should use the condensed version of the nack/ack when possible
		for(int packetId : nackMap.keySet()) {
			ctx.write(new RakNetNACK(packetId)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
		for(int packetId : ackMap.keySet()) {
			ctx.write(new RakNetACK(packetId)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
		nackMap.clear();
		ackMap.clear();
		ctx.flush();
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
