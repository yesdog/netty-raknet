package raknetserver.pipeline.raknet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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

	protected static final int WINDOW = 4096;
	protected static final int HALF_WINDOW = WINDOW / 2;
	protected static final long RTT_FLOOR = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS); //millis

	protected static final PacketHandlerRegistry<RakNetPacketReliabilityHandler, RakNetPacket> registry = new PacketHandlerRegistry<>();
	static {
		registry.register(RakNetEncapsulatedData.class, (ctx, handler, packet) -> handler.handleEncapsulatedData(ctx, packet));
		registry.register(RakNetACK.class, (ctx, handler, packet) -> handler.handleAck(packet));
		registry.register(RakNetNACK.class, (ctx, handler, packet) -> handler.handleNack(packet));
	}

	protected final Channel channel;
	protected final IntSortedSet nackSet = new IntRBTreeSet(IntComparators.NATURAL_COMPARATOR);
	protected final IntSortedSet ackSet = new IntRBTreeSet(IntComparators.NATURAL_COMPARATOR);
	protected final IntOpenHashSet handledSet = new IntOpenHashSet();
	protected final Int2ObjectOpenHashMap<RakNetEncapsulatedData> sentPackets = new Int2ObjectOpenHashMap<>();
	protected final IntPredicate removalPredicate = x -> !idWithinWindow(x);

	protected int lastReceivedSeqId = 0;
	protected int nextSendSeqId = 0;
	protected long minRTT = TimeUnit.NANOSECONDS.convert(2, TimeUnit.SECONDS);

	public RakNetPacketReliabilityHandler(Channel channel) {
		this.channel = channel;
		startResendTimer();
	}

	private void startResendTimer() {
		channel.eventLoop().schedule(() -> {
			if (channel.isOpen()) {
				startResendTimer();
				resendTick();
			}
		}, minRTT, TimeUnit.NANOSECONDS);
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
		final int packetSeqId = packet.getSeqId();
		ackSet.add(packetSeqId);
		nackSet.remove(packetSeqId);
		if (!idWithinWindow(packetSeqId) || handledSet.contains(packetSeqId)) { //ignore duplicate packet
			return;
		}
		handledSet.add(packetSeqId);
		if (UINT.B3.minusWrap(packetSeqId, lastReceivedSeqId) > 0) { //can be zero on the first packet only
			lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
			while (lastReceivedSeqId != packetSeqId) { //nack any missed packets before this one
				if (!handledSet.contains(lastReceivedSeqId)) {
					nackSet.add(lastReceivedSeqId); //add missing packets to nack set
				}
				lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
			}
		}
		packet.getPackets().forEach(ctx::fireChannelRead); //read encapsulated packets
	}

	protected void handleAck(RakNetACK ack) {
		for (REntry entry : ack.getEntries()) {
			final int idStart = entry.idStart;
			final int idFinish = entry.idFinish;
			final int idDiff = UINT.B3.minusWrap(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (ack confirm range)");
			}
			for (int i = 0; i <= idDiff; i++) {
				final int packetId = UINT.B3.plus(idStart, i);
				RakNetEncapsulatedData packet = sentPackets.remove(packetId);
				if (packet != null) {
					minRTT = Math.min(minRTT, Math.max(packet.timeSinceSend(), RTT_FLOOR));
				}
			}
		}
	}

	protected void handleNack(RakNetNACK nack) {
		for (REntry entry : nack.getEntries()) {
			final int idStart = entry.idStart;
			final int idFinish = entry.idFinish;
			final int idDiff = UINT.B3.minusWrap(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (nack resend range)");
			}
			for (int i = 0; i <= idDiff; i++) {
				final RakNetEncapsulatedData packet = sentPackets.remove(UINT.B3.plus(idStart, i));
				if (packet != null) {
					sendPacket(packet, null);
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
			sendPacket(new RakNetEncapsulatedData((EncapsulatedPacket) msg), promise);
		} else {
			ctx.writeAndFlush(msg, promise);
		}
	}

	protected void sendPacket(RakNetEncapsulatedData packet, ChannelPromise promise) {
		packet.setSeqId(nextSendSeqId);
		nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
		sentPackets.put(packet.getSeqId(), packet);
		packet.refreshResend();
		if (promise != null) {
			channel.writeAndFlush(packet, promise);
		} else {
			channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		}
	}

	protected void resendTick() {
		nackSet.removeIf(removalPredicate);
		handledSet.removeIf(removalPredicate);
		if (!ackSet.isEmpty()) {
			channel.writeAndFlush(new RakNetACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			ackSet.clear();
		}
		if (!nackSet.isEmpty()) {
			channel.writeAndFlush(new RakNetNACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			nackSet.clear();
		}

		for (RakNetEncapsulatedData packet : sentPackets.values()) {
			if (packet.resendTick()) {
				sentPackets.remove(packet.getSeqId());
				sendPacket(packet, null); //resend packet
			}
		}
	}
}
