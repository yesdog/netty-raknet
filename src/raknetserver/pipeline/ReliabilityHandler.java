package raknetserver.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import raknetserver.RakNetServer;
import raknetserver.frame.Frame;
import raknetserver.packet.FramedData;
import raknetserver.packet.Reliability;
import raknetserver.packet.Reliability.REntry;
import raknetserver.packet.Reliability.ACK;
import raknetserver.packet.Reliability.NACK;
import raknetserver.utils.Constants;
import raknetserver.utils.UINT;

/*TODO:
Pool EncapsulatedPackets in a big index, keep priority.
On NACK or Resend of a RakNet data packet, prpogate to
big index and have those packets reschedule themselves correctly.

Use reschedule or nack of RNEncapData as event to individual packets?
or on this event, we just retransmit the packet into the pipeline?

*/

//TODO: real rescheduling with priority

/*
TODO: new scheduling idea.
Keep retry counts associated with frames. Older frames will
naturally end up with longer delays quicker. Tick delay for
unreliable should always be zero.
 */

public class ReliabilityHandler extends ChannelDuplexHandler {

    public static final String NAME = "rn-reliability";

    protected final IntSortedSet nackSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
    protected final IntSortedSet ackSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
    protected final Int2ObjectRBTreeMap<FramedData> sentPackets = new Int2ObjectRBTreeMap<>(UINT.B3.COMPARATOR);

    protected int lastReceivedSeqId = 0;
    protected int nextSendSeqId = 0;
    protected boolean backPressureActive = false;
    protected FramedData queuedPacket = FramedData.create();
    protected RakNetServer.Metrics metrics;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        metrics = ctx.channel().attr(RakNetServer.RN_METRICS).get();
        assert metrics != null;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        queuedPacket.release();
        queuedPacket = null;
        sentPackets.values().forEach(ReferenceCountUtil::release);
        sentPackets.clear();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof Reliability.ACK) {
                handleAck((Reliability.ACK) msg);
            } else if (msg instanceof Reliability.NACK) {
                handleNack((Reliability.NACK) msg);
            } else if (msg instanceof FramedData) {
                handleEncapsulatedData(ctx, (FramedData) msg);
            } else {
                ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
        FlushTickDriver.checkTick(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Frame) {
            final Frame packet = (Frame) msg;
            try {
                queuePacket(ctx, packet);
                promise.trySuccess(); //TODO: more accurate way to trigger these?
                metrics.incrOutPacket(1);
            } finally {
                packet.release();
            }
        } else {
            if (msg instanceof RakNetServer.Tick) {
                tick(ctx, ((RakNetServer.Tick) msg).getTicks());
                return; //TODO: UDP channel library crashes if it gets anything besides a ByteBuf....
            }
            ctx.writeAndFlush(msg, promise);
        }
        Constants.packetLossCheck(sentPackets.size(), "unconfirmed sent packets");
    }

    protected void handleEncapsulatedData(ChannelHandlerContext ctx, FramedData packet) {
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
        metrics.incrRecv(1);
        metrics.incrInPacket(packet.getNumPackets());
        packet.readTo(ctx);
    }

    protected void handleAck(ACK ack) {
        int nAck = 0;
        int nIterations = 0;
        for (REntry entry : ack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final FramedData packet = sentPackets.remove(id);
                if (packet != null) {
                    metrics.measureSendAttempts(packet.getSendAttempts());
                    packet.release();
                    nAck++;
                }
                Constants.packetLossCheck(nIterations++, "ack confirm range");
            }
        }
        metrics.incrAckRecv(nAck);
    }

    protected void handleNack(NACK nack) {
        int nNack = 0;
        int nIterations = 0;
        for (REntry entry : nack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final FramedData packet = sentPackets.get(id);
                if (packet != null) {
                    packet.scheduleResend();
                    nNack++;
                }
                Constants.packetLossCheck(nIterations++, "nack confirm range");
            }
        }
        metrics.incrNackRecv(nNack);
    }

    //TODO: frame lock support! special call that makes sure no frames are sent until all current frames are ackd
    //on lock, flush flames so everything is queued and sent at least once, set lock, then block all frames with
    //resend count of 0, until non-0 frames are sent
    protected void tick(ChannelHandlerContext ctx, int nTicks) {
        //all data flushed in order of priority
        if (!ackSet.isEmpty()) {
            ctx.writeAndFlush(new ACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            metrics.incrAckSend(ackSet.size());
            ackSet.clear();
        }
        if (!nackSet.isEmpty()) {
            ctx.writeAndFlush(new NACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            metrics.incrNackSend(nackSet.size());
            nackSet.clear();
        }
        flushPacket();
        final int maxResend = Constants.RESEND_PER_TICK * nTicks;
        final ObjectIterator<FramedData> packetItr = sentPackets.values().iterator();
        int nSent = 0;
        //TODO: remove unreliable?
        while (packetItr.hasNext()) {
            final FramedData packet = packetItr.next();
            //always evaluate resendTick
            if (packet.resendTick(nTicks) && (nSent < maxResend || packet.getSendAttempts() == 0)) {
                sendPacketRaw(ctx, packet);
                nSent++;
                if (packet.getSendAttempts() > 1) {
                    metrics.incrResend(1);
                }
            }
        }
        if (sentPackets.size() > Constants.BACK_PRESSURE_HIGH_WATERMARK) {
            updateBackPressure(ctx, true);
        } else if (sentPackets.size() < Constants.BACK_PRESSURE_LOW_WATERMARK) {
            updateBackPressure(ctx, false);
        }
        Constants.packetLossCheck(sentPackets.size(), "resend queue");
    }

    protected void queuePacket(ChannelHandlerContext ctx, Frame frame) {
        final int maxPacketSize = ctx.channel().attr(RakNetServer.MTU).get() - 100;
        if (!frame.getReliability().isReliable) {
            final FramedData packet = FramedData.create();
            packet.addPacket(frame);
            assignFrameId(packet);
            sendPacketRaw(ctx, packet);
            return;
        }
        if (!queuedPacket.isEmpty() && (queuedPacket.getRoughPacketSize() + frame.getRoughPacketSize()) > maxPacketSize) {
            flushPacket();
        }
        if (!queuedPacket.isEmpty()) {
            metrics.incrJoin(1);
        }
        queuedPacket.addPacket(frame);
    }

    protected void assignFrameId(FramedData packet) {
        packet.setSeqId(nextSendSeqId);
        nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
    }

    protected void sendPacketRaw(ChannelHandlerContext ctx, FramedData packet) {
        final long avgRTT = ctx.channel().attr(RakNetServer.RTT).get();
        packet.refreshResend((int) (avgRTT / FlushTickDriver.TICK_RESOLUTION)); // number of ticks per RTT
        ctx.writeAndFlush(packet.retain()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        metrics.incrSend(1);
    }

    protected void flushPacket() {
        if (!queuedPacket.isEmpty()) {
            assignFrameId(queuedPacket);
            sentPackets.put(queuedPacket.getSeqId(), queuedPacket);
            queuedPacket = FramedData.create();
        }
    }

    protected void updateBackPressure(ChannelHandlerContext ctx, boolean enabled) {
        if (backPressureActive == enabled) {
            return;
        }
        backPressureActive = enabled;
        ctx.fireChannelRead(backPressureActive ? RakNetServer.BackPressure.ON : RakNetServer.BackPressure.OFF);
    }

}
