package raknetserver.pipeline.raknet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
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

    public static final String NAME = "rns-rn-reliability";

    protected static final int RTT_WEIGHT = 8;
    protected static final long TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS);
    protected static final long COARSE_TIMER_RESOLUTION = 50; //in ms, limited by netty timer resolution

    protected static final PacketHandlerRegistry<RakNetPacketReliabilityHandler, RakNetPacket> registry = new PacketHandlerRegistry<>();

    static {
        registry.register(RakNetEncapsulatedData.class, (ctx, handler, packet) -> handler.handleEncapsulatedData(ctx, packet));
        registry.register(RakNetACK.class, (ctx, handler, packet) -> handler.handleAck(packet));
        registry.register(RakNetNACK.class, (ctx, handler, packet) -> handler.handleNack(packet));
    }

    protected final Channel channel;
    protected final RakNetServer.Metrics metrics;
    protected final IntSortedSet nackSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
    protected final IntSortedSet ackSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
    protected final Int2ObjectRBTreeMap<RakNetEncapsulatedData> sentPackets = new Int2ObjectRBTreeMap<>(UINT.B3.COMPARATOR);

    protected int lastReceivedSeqId = 0;
    protected int nextSendSeqId = 0;
    protected long avgRTT = TimeUnit.NANOSECONDS.convert(400, TimeUnit.MILLISECONDS);
    protected long tickAccum = 0;
    protected long lastTickAccum = System.nanoTime();
    protected boolean backPressureActive = false;
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
        metrics.incrRecv(1);
        metrics.incrInPacket(packet.getPackets().size());
    }

    protected void handleAck(RakNetACK ack) {
        int nAck = 0;
        for (REntry entry : ack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final RakNetEncapsulatedData packet = sentPackets.remove(id);
                if (packet != null) {
                    final long rtt = Math.max(packet.timeSinceSend(), TICK_RESOLUTION);
                    if (rtt <= Constants.MAX_RTT) {
                        avgRTT = (avgRTT * (RTT_WEIGHT - 1) + rtt) / RTT_WEIGHT;
                    }
                    metrics.measureRTTns(rtt);
                    metrics.measureSendAttempts(packet.getSendAttempts());
                }
                if (nAck++ > Constants.MAX_PACKET_LOSS) {
                    throw new DecoderException("Too big packet loss (ack confirm range)");
                }
            }
        }
        metrics.incrAckRecv(nAck);
    }

    protected void handleNack(RakNetNACK nack) {
        int nNack = 0;
        for (REntry entry : nack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final RakNetEncapsulatedData packet = sentPackets.get(id);
                if (packet != null) {
                    packet.scheduleResend();
                }
                if (nNack++ > Constants.MAX_PACKET_LOSS) {
                    throw new DecoderException("Too big packet loss (ack confirm range)");
                }
            }
        }
        metrics.incrNackRecv(nNack);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof EncapsulatedPacket) {
            if (sentPackets.size() > Constants.MAX_PACKET_LOSS) {
                throw new DecoderException("Too big packet loss (unconfirmed sent packets)");
            }
            queuePacket((EncapsulatedPacket) msg);
            maybeTick();
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
            channel.writeAndFlush(new RakNetACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            metrics.incrAckSend(ackSet.size());
            ackSet.clear();
        }
        if (!nackSet.isEmpty()) {
            channel.writeAndFlush(new RakNetNACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            metrics.incrNackSend(nackSet.size());
            nackSet.clear();
        }
        flushPacket();
        final int maxResend = Constants.RESEND_PER_TICK * nTicks;
        final ObjectIterator<RakNetEncapsulatedData> packetItr = sentPackets.values().iterator();
        int nResent = 0;
        while (packetItr.hasNext()) {
            final RakNetEncapsulatedData packet = packetItr.next();
            if (packet.resendTick(nTicks) && nResent < maxResend) { //always evaluate resendTick
                sendPacketRaw(packet);
                if (packet.getSendAttempts() > 1) {
                    metrics.incrResend(1);
                    nResent++;
                }
            }
        }
        if (sentPackets.size() > Constants.MAX_PACKET_LOSS) {
            throw new DecoderException("Too big packet loss (resend queue)");
        } else if (sentPackets.size() > Constants.BACK_PRESSURE_HIGH_WATERMARK) {
            updateBackPressure(true);
        } else if (sentPackets.size() < Constants.BACK_PRESSURE_LOW_WATERMARK) {
            updateBackPressure(false);
        }
    }

    protected void queuePacket(EncapsulatedPacket packet) {
        final int maxPacketSize = channel.attr(RakNetConstants.MTU).get() - 100;
        if (!queuedPacket.isEmpty() && (queuedPacket.getRoughPacketSize() + packet.getRoughPacketSize()) > maxPacketSize) {
            flushPacket();
        }
        if (!queuedPacket.getPackets().isEmpty()) {
            metrics.incrJoin(1);
        }
        queuedPacket.getPackets().add(packet);
    }

    protected void registerPacket(RakNetEncapsulatedData packet) {
        packet.setSeqId(nextSendSeqId);
        nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
        sentPackets.put(packet.getSeqId(), packet);
    }

    protected void sendPacketRaw(RakNetEncapsulatedData packet) {
        packet.refreshResend((int) (avgRTT / TICK_RESOLUTION)); // number of ticks per RTT
        channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        metrics.incrSend(1);
    }

    protected void flushPacket() {
        if (!queuedPacket.isEmpty()) {
            registerPacket(queuedPacket);
            queuedPacket = new RakNetEncapsulatedData();
        }
    }

    protected void updateBackPressure(boolean enabled) {
        if (backPressureActive == enabled) {
            return;
        }
        backPressureActive = enabled;
        channel.pipeline().context(NAME).fireChannelRead(
                backPressureActive ? RakNetServer.BackPressure.ON : RakNetServer.BackPressure.OFF);
    }

}
