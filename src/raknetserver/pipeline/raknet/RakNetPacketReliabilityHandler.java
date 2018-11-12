package raknetserver.pipeline.raknet;

import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
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
	protected static final int CONTROL_INTERVAL = 50; //millis
	protected static final int ACK_REPEAT = 5;

	protected final IntSortedSet nackSet = new IntRBTreeSet();
	protected final IntSortedSet ackSet = new IntRBTreeSet();
	protected final Int2ObjectOpenHashMap<RakNetEncapsulatedData> recvStash = new Int2ObjectOpenHashMap<>();
	protected final Int2ObjectOpenHashMap<RakNetEncapsulatedData> sentPackets = new Int2ObjectOpenHashMap<>();
	protected int lastReceivedSeqId = -1;
	protected long lastFlush = 0l;
	protected int nextSendSeqId = 0;

	protected void processEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		int packetSeqId = packet.getSeqId();
		//can now update last received seq id
		lastReceivedSeqId = packetSeqId;
		//read encapsulated packets
		packet.getPackets().forEach(ctx::fireChannelRead);
		recvStash.remove(packetSeqId);
		for (int i = 0 ; i < ACK_REPEAT ; i++) {
			// lets be kind and repeat our acks a few times,
			// the client resend buffer will thank us.
			ackSet.add(UINT.B3.minus(packetSeqId, i + 1));
		}
	}

	protected void handleEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		int packetSeqId = packet.getSeqId();
		int seqIdDiff = UINT.B3.minus(packetSeqId, lastReceivedSeqId);
		//ignore duplicate packet
		if (seqIdDiff == 0 || seqIdDiff > HALF_WINDOW) {
			return;
		}
		if (seqIdDiff > Constants.MAX_PACKET_LOSS) {
			throw new DecoderException("Too big packet loss from client");
		}
		ackSet.add(packetSeqId);
		nackSet.remove(packetSeqId);
		//packet not needed yet
		if (seqIdDiff > 1) {
			// check missing id range and add to nack map
			for (int i = 1 ; i < seqIdDiff ; i++) {
				int nextId = UINT.B3.plus(lastReceivedSeqId, i);
				if (!recvStash.containsKey(nextId))
					nackSet.add(nextId);
			}
			recvStash.put(packetSeqId, packet);
		} else {
			//packet needed now
			processEncapsulatedData(ctx, packet);
			//restore any cached sequence available
			for (int nextId = UINT.B3.plus(packetSeqId, 1); recvStash.containsKey(nextId); nextId = UINT.B3.plus(nextId, 1)) {
				processEncapsulatedData(ctx, recvStash.get(nextId));
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
		flushControlResponses(ctx);
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
		flushControlResponses(ctx);
	}

	protected void flushControlResponses(ChannelHandlerContext ctx) {
		long dt = System.currentTimeMillis() - lastFlush;
		if (dt < CONTROL_INTERVAL) {
			// we have these nice dense nack/ack packets, lets be kind towards
			// the MTU, and slam the client with less small packets
			return;
		}
		lastFlush = System.currentTimeMillis();
		if (!nackSet.isEmpty()) {
			ctx.writeAndFlush(new RakNetNACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			nackSet.clear();
		}
		if (!ackSet.isEmpty()) {
			ctx.writeAndFlush(new RakNetACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			ackSet.clear();
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
