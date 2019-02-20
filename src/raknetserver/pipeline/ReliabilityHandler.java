package raknetserver.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import raknetserver.RakNetServer;
import raknetserver.frame.Frame;
import raknetserver.packet.FrameSet;
import raknetserver.packet.Reliability;
import raknetserver.packet.Reliability.REntry;
import raknetserver.packet.Reliability.ACK;
import raknetserver.packet.Reliability.NACK;
import raknetserver.utils.Constants;
import raknetserver.utils.UINT;

public class ReliabilityHandler extends ChannelDuplexHandler {

    public static final String NAME = "rn-reliability";

    protected final IntSortedSet nackSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
    protected final IntSortedSet ackSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
    protected final ObjectSortedSet<Frame> frameQueue = new ObjectRBTreeSet<>(Frame.COMPARATOR);
    protected final Int2ObjectMap<FrameSet> pendingFrameSets = new Int2ObjectOpenHashMap<>();

    protected int lastReceivedSeqId = 0;
    protected int nextSendSeqId = 0;
    protected int resendGauge = 0;
    protected int burstTokens = 0;
    protected boolean backPressureActive = false;
    protected RakNetServer.MetricsLogger metrics;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        metrics = ctx.channel().attr(RakNetServer.RN_METRICS).get();
        assert metrics != null;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        frameQueue.forEach(Frame::release);
        frameQueue.clear();
        pendingFrameSets.values().forEach(FrameSet::release);
        pendingFrameSets.clear();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof Reliability.ACK) {
                readAck((Reliability.ACK) msg);
            } else if (msg instanceof Reliability.NACK) {
                readNack((Reliability.NACK) msg);
            } else if (msg instanceof FrameSet) {
                readFrameSet(ctx, (FrameSet) msg);
            } else {
                ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Frame) {
            queueFrame((Frame) msg);
            promise.trySuccess(); //TODO: more accurate way to trigger these?
        } else if (msg instanceof RakNetServer.Tick) {
            tick(ctx, ((RakNetServer.Tick) msg).getTicks());
            promise.trySuccess();
        } else {
            ctx.writeAndFlush(msg, promise);
        }
        Constants.packetLossCheck(pendingFrameSets.size(), "unconfirmed sent packets");
    }


    protected void readFrameSet(ChannelHandlerContext ctx, FrameSet frameSet) {
        final int packetSeqId = frameSet.getSeqId();
        ackSet.add(packetSeqId);
        nackSet.remove(packetSeqId);
        if (UINT.B3.minusWrap(packetSeqId, lastReceivedSeqId) > 0) {
            lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
            while (lastReceivedSeqId != packetSeqId) { //nack any missed packets before this one
                nackSet.add(lastReceivedSeqId); //add missing packets to nack set
                lastReceivedSeqId = UINT.B3.plus(lastReceivedSeqId, 1);
            }
        }
        metrics.packetsIn(1);
        metrics.framesIn(frameSet.getNumPackets());
        metrics.bytesIn(frameSet.getRoughSize());
        frameSet.createFrames(ctx::fireChannelRead);
    }

    protected void readAck(ACK ack) {
        int ackdBytes = 0;
        int nIterations = 0;
        for (REntry entry : ack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final FrameSet packet = pendingFrameSets.remove(id);
                if (packet != null) {
                    ackdBytes += packet.getRoughSize();
                    adjustResendGauge(1);
                    packet.release();
                }
                Constants.packetLossCheck(nIterations++, "ack confirm range");
            }
        }
        metrics.bytesACKd(ackdBytes);
    }

    protected void readNack(NACK nack) {
        int bytesNACKd = 0;
        int nIterations = 0;
        for (REntry entry : nack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final FrameSet frameSet = pendingFrameSets.remove(id);
                if (frameSet != null) {
                    bytesNACKd += frameSet.getRoughSize();
                    recallFrameSet(frameSet);
                }
                Constants.packetLossCheck(nIterations++, "nack confirm range");
            }
        }
        metrics.bytesNACKd(bytesNACKd);
    }

    protected void tick(ChannelHandlerContext ctx, int nTicks) {
        //all data sent in order of priority
        if (!ackSet.isEmpty()) {
            ctx.writeAndFlush(new ACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            metrics.acksSent(ackSet.size());
            ackSet.clear();
        }
        if (!nackSet.isEmpty()) {
            ctx.writeAndFlush(new NACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            metrics.nacksSent(nackSet.size());
            nackSet.clear();
        }
        final ObjectIterator<FrameSet> packetItr = pendingFrameSets.values().iterator();
        final long avgRTT = ctx.channel().attr(RakNetServer.RTT).get();
        final int maxTicks = (int) (avgRTT / FlushTickDriver.TICK_RESOLUTION) + Constants.RETRY_TICK_OFFSET;
        while (packetItr.hasNext()) {
            final FrameSet frameSet = packetItr.next();
            if (frameSet.incrTick(nTicks) >= maxTicks) {
                packetItr.remove();
                recallFrameSet(frameSet);
            }
        }
        updateBurstTokens(nTicks);
        produceFrameSets(ctx);
        if (frameQueue.size() > Constants.BACK_PRESSURE_HIGH_WATERMARK) {
            updateBackPressure(ctx, true);
        } else if (frameQueue.size() < Constants.BACK_PRESSURE_LOW_WATERMARK) {
            updateBackPressure(ctx, false);
        }
        Constants.packetLossCheck(pendingFrameSets.size(), "resend queue");
    }

    protected void queueFrame(Frame frame) {
        frameQueue.add(frame);
        Constants.packetLossCheck(frameQueue.size(), "frame queue");
    }

    protected void produceFrameSet(ChannelHandlerContext ctx, int maxSize) {
        final ObjectIterator<Frame> itr = frameQueue.iterator();
        final FrameSet frameSet = FrameSet.create();
        while (itr.hasNext()) {
            final Frame frame = itr.next();
            if (frameSet.getRoughSize() + frame.getRoughPacketSize() > maxSize) {
                if (frameSet.isEmpty()) {
                    throw new DecoderException("Finished frame larger than the MTU by " + (frame.getRoughPacketSize() - maxSize));
                }
                break;
            }
            itr.remove();
            frameSet.addPacket(frame);
        }
        if (!frameSet.isEmpty()) {
            frameSet.setSeqId(nextSendSeqId);
            nextSendSeqId = UINT.B3.plus(nextSendSeqId, 1);
            pendingFrameSets.put(frameSet.getSeqId(), frameSet);
            ctx.writeAndFlush(frameSet.retain()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            frameSet.touch("Added to pending frameset list");
            assert frameSet.refCnt() > 0;
            metrics.packetsOut(1);
            metrics.framesOut(frameSet.getNumPackets());
            metrics.bytesOut(frameSet.getRoughSize());
        }
    }

    protected void adjustResendGauge(int n) {
        //clamped gauge, can rebound more easily
        resendGauge = Math.max(
                -Constants.DEFAULT_PENDING_FRAME_SETS,
                Math.min(Constants.DEFAULT_PENDING_FRAME_SETS, resendGauge + n)
        );
    }

    protected void updateBurstTokens(int nTicks) {
        //gradual increment or decrement for burst tokens, unless unused
        final boolean burstUnused = pendingFrameSets.size() < burstTokens / 2;
        if (resendGauge > 1 && !burstUnused) {
            burstTokens += nTicks;
        } else if (resendGauge < -1 || burstUnused) {
            burstTokens -= nTicks;
        }
        burstTokens = Math.max(Math.min(burstTokens, Constants.MAX_PENDING_FRAME_SETS), 0);
        metrics.measureBurstTokens(burstTokens);
    }

    protected void produceFrameSets(ChannelHandlerContext ctx) {
        final Integer storedMTU = ctx.channel().attr(RakNetServer.MTU).get();
        if (storedMTU == null) {
            return;
        }
        final int maxSize = storedMTU - FrameSet.HEADER_SIZE - Frame.HEADER_SIZE;
        final int maxPendingFrameSets = Constants.DEFAULT_PENDING_FRAME_SETS + burstTokens;
        while (pendingFrameSets.size() < maxPendingFrameSets && !frameQueue.isEmpty()) {
            produceFrameSet(ctx, maxSize);
        }
    }

    protected void recallFrameSet(FrameSet frameSet) {
        try {
            adjustResendGauge(-1);
            metrics.bytesRecalled(frameSet.getRoughSize());
            frameSet.touch("Recalled");
            frameSet.createFrames(frame -> {
                if (frame.getReliability().isReliable) {
                    queueFrame(frame);
                } else {
                    frame.release();
                }
            });
        } finally {
            frameSet.release();
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
