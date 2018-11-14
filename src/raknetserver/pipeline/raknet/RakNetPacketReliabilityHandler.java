package raknetserver.pipeline.raknet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntComparators;
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
import java.util.function.IntPredicate;

public class RakNetPacketReliabilityHandler extends ChannelDuplexHandler {

	protected static final PacketHandlerRegistry<RakNetPacketReliabilityHandler, RakNetPacket> registry = new PacketHandlerRegistry<>();
	static {
		registry.register(RakNetEncapsulatedData.class, (ctx, handler, packet) -> handler.handleEncapsulatedData(ctx, packet));
		registry.register(RakNetACK.class, (ctx, handler, packet) -> handler.handleAck(ctx, packet));
		registry.register(RakNetNACK.class, (ctx, handler, packet) -> handler.handleNack(ctx, packet));
	}

	protected static final int WINDOW = 4096;
	protected static final int HALF_WINDOW = WINDOW / 2;
	protected static final int CONTROL_INTERVAL = 50; //millis
	protected static final int RETRY_TICK_MULTIPLIER = 2; //resend window starts at 100-150ms
	protected static final byte[] FIBONACCI = new byte[] { 1, 1, 2, 3, 5, 8, 13, 21, 34 }; //used for retry backoff

	protected final IntSortedSet nackSet = new IntRBTreeSet(IntComparators.NATURAL_COMPARATOR);
	protected final IntSortedSet ackSet = new IntRBTreeSet(IntComparators.NATURAL_COMPARATOR);
	protected final IntOpenHashSet handledSet = new IntOpenHashSet();
	protected final Int2ByteOpenHashMap sentPacketResendTicks = new Int2ByteOpenHashMap();
	protected final Int2ByteOpenHashMap sentPacketResendAttempts = new Int2ByteOpenHashMap();
	protected final Int2ObjectOpenHashMap<RakNetEncapsulatedData> sentPackets = new Int2ObjectOpenHashMap<>();
	protected final IntPredicate removalPredicate = x -> !idWithinWindow(x);
	protected int lastReceivedSeqId = 0;
	protected int nextSendSeqId = 0;

	// metrics
	protected long duplicatesReceived = 0;
	protected long timedResends = 0;
	protected long nackResends = 0;
	protected long nacksSent = 0;
	protected long flushesDone = 0;

	protected final Channel channel;

	public RakNetPacketReliabilityHandler(Channel channel) {
		this.channel = channel;
		startFlushTimer();
		sentPacketResendTicks.defaultReturnValue((byte)0);
		sentPacketResendAttempts.defaultReturnValue((byte)0);
	}

	private void startFlushTimer() {
		channel.eventLoop().schedule(() -> {
			if (channel.isOpen()) {
				flushControlResponses();
				startFlushTimer();
			}
		}, CONTROL_INTERVAL, TimeUnit.MILLISECONDS);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof RakNetPacket) {
			registry.handle(ctx, this, (RakNetPacket) msg);
		} else {
			ctx.fireChannelRead(msg);
		}
	}

	protected boolean idWithinWindow(int id) {
		return Math.abs(UINT.B3.minusWrap(id, lastReceivedSeqId)) < HALF_WINDOW;
	}

	protected void handleEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		int packetSeqId = packet.getSeqId();
		ackSet.add(packetSeqId);
		nackSet.remove(packetSeqId);
		if (!idWithinWindow(packetSeqId) || handledSet.contains(packetSeqId)) { //ignore duplicate packet
			duplicatesReceived++;
			return;
		}
		handledSet.add(packetSeqId);
		if (UINT.B3.minusWrap(packetSeqId, lastReceivedSeqId) > 0) { //can be zero on the first packet only
			lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
			while (lastReceivedSeqId != packetSeqId) {
				//add missing packets to nack
				if (!handledSet.contains(lastReceivedSeqId)) {
					nackSet.add(lastReceivedSeqId);
				}
				lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
			}
		}
		//read encapsulated packets
		packet.getPackets().forEach(ctx::fireChannelRead);
	}

	protected void handleAck(ChannelHandlerContext ctx, RakNetACK ack) {
		for (REntry entry : ack.getEntries()) {
			int idStart = entry.idStart;
			int idFinish = entry.idFinish;
			int idDiff = UINT.B3.minusWrap(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (ack confirm range)");
			}
			for (int i = 0; i <= idDiff; i++) {
				int packetId = UINT.B3.plus(idStart, i);
				sentPackets.remove(packetId);
				sentPacketResendTicks.remove(packetId);
				sentPacketResendAttempts.remove(packetId);
			}
		}
	}

	protected void handleNack(ChannelHandlerContext ctx, RakNetNACK nack) {
		for (REntry entry : nack.getEntries()) {
			int idStart = entry.idStart;
			int idFinish = entry.idFinish;
			int idDiff = UINT.B3.minusWrap(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (nack resend range)");
			}
			for (int i = 0; i <= idDiff; i++) {
				RakNetEncapsulatedData packet = sentPackets.remove(UINT.B3.plus(idStart, i));
				if (packet != null) {
					sendPacket(packet, null);
					nackResends++;
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
			RakNetEncapsulatedData outPacket = new RakNetEncapsulatedData((EncapsulatedPacket) msg);
			outPacket.setSeqId(nextSendSeqId);
			nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
			sendPacket(outPacket, promise);
		} else {
			ctx.writeAndFlush(msg, promise);
		}
	}

	protected void sendPacket(RakNetEncapsulatedData packet, ChannelPromise promise) {
		int attempts = sentPacketResendAttempts.get(packet.getSeqId());
		int resendTicks = RETRY_TICK_MULTIPLIER * FIBONACCI[Math.min(attempts, FIBONACCI.length - 1)];
		//TODO: check if this packet had anything important
		//TODO: different order channels for packet types.
		sentPackets.put(packet.getSeqId(), packet);
		sentPacketResendTicks.put(packet.getSeqId(), (byte)resendTicks);
		sentPacketResendAttempts.put(packet.getSeqId(), (byte)(attempts + 1));
		if (promise != null) {
			channel.writeAndFlush(packet, promise);
		} else {
			channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

	//pool ACKs/NACKs and flush on a tick. also resend packets when needed
	protected void flushControlResponses() {
		flushesDone++;
		nackSet.removeIf(removalPredicate);
		handledSet.removeIf(removalPredicate);
		if (!ackSet.isEmpty()) {
			channel.writeAndFlush(new RakNetACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			ackSet.clear();
		}
		if (!nackSet.isEmpty()) {
			channel.writeAndFlush(new RakNetNACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			nacksSent += nackSet.size();
			nackSet.clear();
		}
		for (int sentId : sentPacketResendTicks.keySet()) {
			byte ticks = sentPacketResendTicks.get(sentId);
			if (ticks == 0) {
				sendPacket(sentPackets.get(sentId), null); //resend packet
				timedResends++;
			} else {
				sentPacketResendTicks.put(sentId, (byte)(ticks - 1));
			}
		}
		//TODO: expose this somewhere
		if ((flushesDone % 100) == 0 && lastReceivedSeqId > 2) {
			System.out.println(String.format(
					"RakNet: duplicatesReceived: %d, nackResends: %d, timedResends: %d, nacksSent: %d",
					duplicatesReceived, nackResends, timedResends, nacksSent));
		}
	}
}
