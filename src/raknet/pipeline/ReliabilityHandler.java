package raknet.pipeline;

import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;

import raknet.RakNet;
import raknet.frame.Frame;
import raknet.packet.FrameSet;
import raknet.packet.Reliability;
import raknet.packet.Reliability.REntry;
import raknet.packet.Reliability.ACK;
import raknet.packet.Reliability.NACK;
import raknet.utils.Constants;
import raknet.utils.UINT;

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
    protected RakNet.Config config = null;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        config = (RakNet.Config) ctx.channel().config();
        super.handlerAdded(ctx);
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
            final Frame frame = (Frame) msg;
            queueFrame(frame);
            frame.setPromise(promise);
        } else {
            ctx.write(msg, promise);
        }
        Constants.packetLossCheck(pendingFrameSets.size(), "unconfirmed sent packets");
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        //all data sent in order of priority
        if (!ackSet.isEmpty()) {
            ctx.write(new ACK(ackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            config.getMetrics().acksSent(ackSet.size());
            ackSet.clear();
        }
        if (!nackSet.isEmpty()) {
            ctx.write(new NACK(nackSet)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            config.getMetrics().nacksSent(nackSet.size());
            nackSet.clear();
        }
        final ObjectIterator<FrameSet> packetItr = pendingFrameSets.values().iterator();
        final long deadline = System.nanoTime() - (config.getRTT() + config.getRetryDelay());
        while (packetItr.hasNext()) {
            final FrameSet frameSet = packetItr.next();
            if (frameSet.getSentTime() < deadline) {
                packetItr.remove();
                recallFrameSet(frameSet);
            } else {
                //break; //TODO: FrameSets should be ordered by send time ultimately
            }
        }
        updateBurstTokens();
        produceFrameSets(ctx);
        updateBackPressure(ctx);
        Constants.packetLossCheck(pendingFrameSets.size(), "resend queue");
        super.flush(ctx);
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
        config.getMetrics().packetsIn(1);
        config.getMetrics().framesIn(frameSet.getNumPackets());
        config.getMetrics().bytesIn(frameSet.getRoughSize());
        frameSet.createFrames(ctx::fireChannelRead);
    }

    protected void readAck(ACK ack) {
        int ackdBytes = 0;
        int nIterations = 0;
        for (REntry entry : ack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart ; id != max ; id = UINT.B3.plus(id, 1)) {
                final FrameSet frameSet = pendingFrameSets.remove(id);
                if (frameSet != null) {
                    ackdBytes += frameSet.getRoughSize();
                    adjustResendGauge(1);
                    frameSet.succeed();
                    frameSet.release();
                }
                Constants.packetLossCheck(nIterations++, "ack confirm range");
            }
        }
        config.getMetrics().bytesACKd(ackdBytes);
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
        config.getMetrics().bytesNACKd(bytesNACKd);
    }

    protected void queueFrame(Frame frame) {
        frameQueue.add(frame);
        Constants.packetLossCheck(frameQueue.size(), "frame queue");
    }

    protected void adjustResendGauge(int n) {
        //clamped gauge, can rebound more easily
        resendGauge = Math.max(
                -config.getDefaultPendingFrameSets(),
                Math.min(config.getDefaultPendingFrameSets(), resendGauge + n)
        );
    }

    protected void updateBurstTokens() {
        //gradual increment or decrement for burst tokens, unless unused
        final boolean burstUnused = pendingFrameSets.size() < burstTokens / 2;
        if (resendGauge > 1 && !burstUnused) {
            burstTokens += 1;
        } else if (resendGauge < -1 || burstUnused) {
            burstTokens -= 3;
        }
        burstTokens = Math.max(Math.min(burstTokens, config.getMaxPendingFrameSets()), 0);
        config.getMetrics().measureBurstTokens(burstTokens);
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
            frameSet.touch("Added to pending FrameSet list");
            ctx.write(frameSet.retain()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            config.getMetrics().packetsOut(1);
            config.getMetrics().framesOut(frameSet.getNumPackets());
            config.getMetrics().bytesOut(frameSet.getRoughSize());
            assert frameSet.refCnt() > 0;
        }
    }

    protected void produceFrameSets(ChannelHandlerContext ctx) {
        final int mtu = config.getMTU();
        final int maxSize = mtu - FrameSet.HEADER_SIZE - Frame.HEADER_SIZE;
        final int maxPendingFrameSets = config.getDefaultPendingFrameSets() + burstTokens;
        while (pendingFrameSets.size() < maxPendingFrameSets && !frameQueue.isEmpty()) {
            produceFrameSet(ctx, maxSize);
        }
    }

    protected void recallFrameSet(FrameSet frameSet) {
        try {
            adjustResendGauge(-1);
            config.getMetrics().bytesRecalled(frameSet.getRoughSize());
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

    protected void updateBackPressure(ChannelHandlerContext ctx) {
        final int queuedBytes = getQueuedBytes();
        final boolean newBackPressureActive;
        if (queuedBytes > config.getWriteBufferHighWaterMark()) {
            newBackPressureActive = true;
        } else if (queuedBytes < config.getWriteBufferLowWaterMark()) {
            newBackPressureActive = false;
        } else {
            newBackPressureActive = backPressureActive;
        }
        if (backPressureActive == newBackPressureActive) {
            return;
        }
        backPressureActive = newBackPressureActive;
        ctx.channel().attr(RakNet.WRITABLE).set(backPressureActive ? Boolean.FALSE : Boolean.TRUE);
        ctx.fireChannelWritabilityChanged();
    }

    protected int getQueuedBytes() {
        //TODO: some more accurate way to measure these
        //return frameQueue.size() * config.getMTU();
        int byteSize = 0;
        for (Frame frame : frameQueue) {
            byteSize += frame.getRoughPacketSize();
        }
        return byteSize;
    }

}
