package network.ycc.raknet.pipeline;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.frame.Frame;
import network.ycc.raknet.packet.FrameSet;
import network.ycc.raknet.packet.Reliability;
import network.ycc.raknet.utils.Constants;
import network.ycc.raknet.utils.UINT;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.ReferenceCountUtil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;

/**
 * This handler handles the bulk of reliable (framed) transport.
 */
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
    protected RakNet.Config config = null; //TODO: not really needed anymore

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        config = RakNet.config(ctx);
        ctx.channel().attr(RakNet.WRITABLE).set(true);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        clearQueue(null);
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
        FlushTickHandler.checkFlushTick(ctx.channel());
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (!ctx.channel().isOpen()) {
            ctx.flush();
            return;
        }
        //all data sent in order of priority
        sendResponses(ctx);
        recallExpiredFrameSets();
        updateBurstTokens();
        produceFrameSets(ctx);
        updateBackPressure(ctx);
        Constants.packetLossCheck(pendingFrameSets.size(), "resend queue");
        ctx.flush();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        //missed some flush ticks, lets catch up on a few things
        if (evt == FlushTickHandler.FLUSH_CATCHUP_SIGNAL) {
            sendResponses(ctx);
            updateBurstTokens();
        }
        ctx.fireUserEventTriggered(evt);
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

    protected void clearQueue(Throwable t) {
        if (t != null) {
            frameQueue.forEach(frame -> {
                if (frame.getPromise() != null) {
                    frame.getPromise().tryFailure(t);
                }
            });
            pendingFrameSets.values().forEach(set -> set.fail(t));
        }
        frameQueue.forEach(Frame::release);
        frameQueue.clear();
        pendingFrameSets.values().forEach(FrameSet::release);
        pendingFrameSets.clear();
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
        frameSet.createFrames(ctx::fireChannelRead);
        ctx.fireChannelReadComplete();
    }

    protected void readAck(Reliability.ACK ack) {
        int ackdBytes = 0;
        int nIterations = 0;
        for (Reliability.REntry entry : ack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart; id != max; id = UINT.B3.plus(id, 1)) {
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

    protected void readNack(Reliability.NACK nack) {
        int bytesNACKd = 0;
        int nIterations = 0;
        for (Reliability.REntry entry : nack.getEntries()) {
            final int max = UINT.B3.plus(entry.idFinish, 1);
            for (int id = entry.idStart; id != max; id = UINT.B3.plus(id, 1)) {
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
        if (frame.getRoughPacketSize() > config.getMTU()) {
            throw new CorruptedFrameException(
                    "Finished frame larger than the MTU by " + (frame.getRoughPacketSize() - config
                            .getMTU()));
        }
        frameQueue.add(frame);
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

    protected void sendResponses(ChannelHandlerContext ctx) {
        if (!ackSet.isEmpty()) {
            ctx.write(new Reliability.ACK(ackSet)).addListener(RakNet.INTERNAL_WRITE_LISTENER);
            config.getMetrics().acksSent(ackSet.size());
            ackSet.clear();
        }
        if (!nackSet.isEmpty() && config.isAutoRead()) { //only nack if we can read
            ctx.write(new Reliability.NACK(nackSet)).addListener(RakNet.INTERNAL_WRITE_LISTENER);
            config.getMetrics().nacksSent(nackSet.size());
            nackSet.clear();
        }
    }

    protected void recallExpiredFrameSets() {
        final ObjectIterator<FrameSet> packetItr = pendingFrameSets.values().iterator();
        //2 sd from mean RTT is about 97% coverage
        final long deadline = System.nanoTime() -
                (config.getRTTNanos() + 2 * config.getRTTStdDevNanos() + config.getRetryDelayNanos());
        while (packetItr.hasNext()) {
            final FrameSet frameSet = packetItr.next();
            if (frameSet.getSentTime() < deadline) {
                packetItr.remove();
                recallFrameSet(frameSet);
            } else {
                //break; //TODO: FrameSets should be ordered by send time ultimately
            }
        }
    }

    protected void produceFrameSet(ChannelHandlerContext ctx, int maxSize) {
        final ObjectIterator<Frame> itr = frameQueue.iterator();
        final FrameSet frameSet = FrameSet.create();
        while (itr.hasNext()) {
            final Frame frame = itr.next();
            assert frame.refCnt() > 0 : "Frame has lost reference";
            if (frameSet.getRoughSize() + frame.getRoughPacketSize() > maxSize) {
                if (frameSet.isEmpty()) {
                    throw new CorruptedFrameException(
                            "Finished frame larger than the MTU by " + (frame.getRoughPacketSize() - maxSize));
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
            ctx.write(frameSet.retain()).addListener(RakNet.INTERNAL_WRITE_LISTENER);
            config.getMetrics().packetsOut(1);
            config.getMetrics().framesOut(frameSet.getNumPackets());
            assert frameSet.refCnt() > 0;
        } else {
            frameSet.release();
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

    //TODO: instead of immediate recall, mark framesets as 'recalled', and flush at flush cycle
    protected void recallFrameSet(FrameSet frameSet) {
        try {
            adjustResendGauge(-1);
            config.getMetrics().bytesRecalled(frameSet.getRoughSize());
            frameSet.touch("Recalled");
            frameSet.createFrames(frame -> {
                if (frame.getReliability().isReliable) {
                    queueFrame(frame);
                } else {
                    frame.getPromise().trySuccess(); //TODO: maybe need a fail here
                    frame.release();
                }
            });
        } finally {
            frameSet.release();
        }
    }

    protected void updateBackPressure(ChannelHandlerContext ctx) {
        final int queuedBytes = getQueuedBytes();
        final boolean oldWritable = ctx.channel().attr(RakNet.WRITABLE).get();
        boolean newWritable = oldWritable;
        if (queuedBytes > config.getMaxQueuedBytes()) {
            final CodecException t = new CodecException("Frame queue is too large");
            clearQueue(t);
            ctx.close();
            throw t;
        } else if (queuedBytes > config.getWriteBufferHighWaterMark()) {
            newWritable = false;
        } else if (queuedBytes < config.getWriteBufferLowWaterMark()) {
            newWritable = true;
        }
        if (newWritable != oldWritable) {
            ctx.channel().attr(RakNet.WRITABLE).set(newWritable ? Boolean.TRUE : Boolean.FALSE);
            ctx.fireChannelWritabilityChanged();
        }
    }

    protected int getQueuedBytes() {
        int byteSize = 0;
        for (Frame frame : frameQueue) {
            byteSize += frame.getRoughPacketSize();
        }
        return byteSize;
    }

}
