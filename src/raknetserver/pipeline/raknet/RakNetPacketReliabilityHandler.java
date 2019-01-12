package raknetserver.pipeline.raknet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import raknetserver.RakNetServer;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.RakNetConstants;
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

	protected static final long COARSE_TIMER_RESOLUTION = 50; //in ms, limited by netty timer resolution
	protected static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS);

	protected static final PacketHandlerRegistry<RakNetPacketReliabilityHandler, RakNetPacket> registry = new PacketHandlerRegistry<>();
	static {
		registry.register(RakNetEncapsulatedData.class, (ctx, handler, packet) -> handler.handleEncapsulatedData(ctx, packet));
		registry.register(RakNetACK.class, (ctx, handler, packet) -> handler.handleAck(packet));
		registry.register(RakNetNACK.class, (ctx, handler, packet) -> handler.handleNack(packet));
	}

	protected final Channel channel;
	protected final RakNetServer.Metrics metrics;
	protected final IntSortedSet nackSet = new IntRBTreeSet(IntComparators.NATURAL_COMPARATOR);
	protected final IntSortedSet ackSet = new IntRBTreeSet(IntComparators.NATURAL_COMPARATOR);
	protected final Int2ObjectMap<RakNetEncapsulatedData> sentPackets = new Int2ObjectOpenHashMap<>();

	protected int lastReceivedSeqId = 0;
	protected int nextSendSeqId = 0;
	protected long minRTT = TimeUnit.NANOSECONDS.convert(2, TimeUnit.SECONDS);
	protected long tickAccum = 0;
	protected long lastTickAccum = System.nanoTime();
	protected RakNetEncapsulatedData queuedPacket = new RakNetEncapsulatedData();

	public RakNetPacketReliabilityHandler(Channel channel, RakNetServer.Metrics metrics) {
		this.channel = channel;
		this.metrics = metrics;
		startCoarseTickTimer();
	}

	private void startCoarseTickTimer() {
		channel.eventLoop().schedule(() -> {
			if (channel.isOpen()) {
				startCoarseTickTimer();
				maybeTick();
			}
		}, COARSE_TIMER_RESOLUTION, TimeUnit.MILLISECONDS);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof RakNetPacket) {
			registry.handle(ctx, this, (RakNetPacket) msg);
			maybeTick();
		} else {
			ctx.fireChannelRead(msg);
		}
	}

	protected void handleEncapsulatedData(ChannelHandlerContext ctx, RakNetEncapsulatedData packet) {
		final int packetSeqId = packet.getSeqId();
		ackSet.add(packetSeqId);
		nackSet.remove(packetSeqId);
		if (UINT.B3.minusWrap(packetSeqId, lastReceivedSeqId) > 0) {
			lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
			while (lastReceivedSeqId != packetSeqId) { //nack any missed packets before this one
				nackSet.add(lastReceivedSeqId); //add missing packets to nack set
				lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
			}
		}
		packet.getPackets().forEach(ctx::fireChannelRead); //read encapsulated packets
		metrics.incrInPacket(1);
	}

	protected void handleAck(RakNetACK ack) {
		for (REntry entry : ack.getEntries()) {
			final int idStart = entry.idStart;
			final int idFinish = entry.idFinish;
			final int idDiff = UINT.B3.minusWrap(idFinish, idStart);
			if (idDiff > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (ack confirm range)");
			}
			metrics.incrAckRecv(idDiff + 1);
			for (int i = 0; i <= idDiff; i++) {
				final int packetId = UINT.B3.plus(idStart, i);
				RakNetEncapsulatedData packet = sentPackets.remove(packetId);
				if (packet != null) {
					final long rtt = Math.max(packet.timeSinceSend(), TICK_RESOLUTION);
					if (rtt < minRTT) {
						minRTT = rtt;
					}
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
			metrics.incrNackRecv(idDiff + 1);
			for (int i = 0; i <= idDiff; i++) {
				final RakNetEncapsulatedData packet = sentPackets.get(UINT.B3.plus(idStart, i));
				if (packet != null) {
					packet.scheduleResend();
				}
			}
		}
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
		if (msg instanceof EncapsulatedPacket) {
			if (sentPackets.size() > Constants.MAX_PACKET_LOSS) {
				throw new DecoderException("Too big packet loss (unconfirmed sent packets)");
			}
			maybeTick();
			queuePacket((EncapsulatedPacket) msg);
			promise.trySuccess();
			metrics.incrOutPacket(1);
		} else {
			ctx.writeAndFlush(msg, promise);
		}
	}

	/*
	The tick fires no faster than the set interval, and is driven
	by a slow (100ms) netty timer as well as the traffic flow itself.
	This could benefit from a higher resolution timer, but the
	traffic flow itself generally does fine as a driver.
	 */
	protected void maybeTick() {
		final long curTime = System.nanoTime();
		tickAccum += curTime - lastTickAccum;
		lastTickAccum = curTime;
		if (tickAccum > TICK_RESOLUTION) {
			final int nTicks = (int) (tickAccum / TICK_RESOLUTION);
			tickAccum = tickAccum % TICK_RESOLUTION;
			tick(nTicks);
		}
	}

	protected void tick(int nTicks) {
		//all data flushed in order of priority
		if (!ackSet.isEmpty()) {
			metrics.incrAckSend(ackSet.size());
			channel.writeAndFlush(new RakNetACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			ackSet.clear();
		}
		if (!nackSet.isEmpty()) {
			metrics.incrNackSend(ackSet.size());
			channel.writeAndFlush(new RakNetNACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			nackSet.clear();
		}
		for (ObjectIterator<RakNetEncapsulatedData> itr = sentPackets.values().iterator() ; itr.hasNext() ; ) {
			final RakNetEncapsulatedData packet = itr.next();
			if (packet.resendTick(nTicks)) {
				itr.remove();
				sendPacket(packet); //resend
				metrics.incrResend(1);
			}
		}
		flushPacket();
		if (sentPackets.size() > Constants.MAX_PACKET_LOSS) {
			throw new DecoderException("Too big packet loss (resend queue)");
		}
	}

	protected void queuePacket(EncapsulatedPacket packet) {
		final int maxPacketSize = channel.attr(RakNetConstants.MTU).get() - 100;
		if (!queuedPacket.isEmpty() && (queuedPacket.getRoughPacketSize() + packet.getRoughPacketSize()) > maxPacketSize) {
			flushPacket();
		}
		queuedPacket.getPackets().add(packet);
		if (queuedPacket.getPackets().size() > 1) {
			metrics.incrJoin(1);
		}
	}

	protected void sendPacket(RakNetEncapsulatedData packet) {
		packet.setSeqId(nextSendSeqId);
		nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
		sentPackets.put(packet.getSeqId(), packet);
		packet.refreshResend((int) (minRTT / TICK_RESOLUTION)); // number of ticks per RTT
		channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		metrics.incrSend(1);
	}

	protected void flushPacket() {
		if (!queuedPacket.isEmpty()) {
			sendPacket(queuedPacket);
			queuedPacket = new RakNetEncapsulatedData();
		}
	}
}
