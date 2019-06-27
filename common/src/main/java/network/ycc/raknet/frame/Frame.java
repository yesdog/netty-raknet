package network.ycc.raknet.frame;

import network.ycc.raknet.packet.FramedPacket;
import network.ycc.raknet.utils.UINT;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import java.util.List;

public final class Frame extends AbstractReferenceCounted {

    public static final FrameComparator COMPARATOR = new FrameComparator();
    public static final int HEADER_SIZE = 24;
    protected static final int SPLIT_FLAG = 0x10;
    private static final ResourceLeakDetector<Frame> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Frame.class);
    private static final Recycler<Frame> recycler = new Recycler<Frame>() {
        @Override
        protected Frame newObject(Handle<Frame> handle) {
            return new Frame(handle);
        }
    };
    private final Recycler.Handle<Frame> handle;
    private boolean hasSplit;
    private int reliableIndex;
    private int sequenceIndex;
    private int orderIndex;
    private int splitCount;
    private int splitID;
    private int splitIndex;
    private FrameData packet = null;
    private ResourceLeakTracker<Frame> tracker = null;
    private ChannelPromise promise = null;

    private Frame(Recycler.Handle<Frame> handle) {
        this.handle = handle;
        setRefCnt(0);
    }

    public static Frame read(ByteBuf buf) {
        final Frame out = createRaw();
        final int flags = buf.readUnsignedByte();
        final int bitLength = buf.readUnsignedShort();
        final int length = (bitLength + Byte.SIZE - 1) / Byte.SIZE; //round up
        final boolean hasSplit = (flags & SPLIT_FLAG) != 0;
        final FramedPacket.Reliability reliability = FramedPacket.Reliability.get(flags >> 5);
        int orderChannel = 0;

        if (reliability.isReliable) {
            out.reliableIndex = buf.readUnsignedMediumLE();
        }
        if (reliability.isSequenced) {
            out.sequenceIndex = buf.readUnsignedMediumLE();
        }
        if (reliability.isOrdered) {
            out.orderIndex = buf.readUnsignedMediumLE();
            orderChannel = buf.readUnsignedByte();
        }
        if (hasSplit) {
            out.splitCount = buf.readInt();
            out.splitID = buf.readUnsignedShort();
            out.splitIndex = buf.readInt();
            out.hasSplit = true;
        }

        out.packet = FrameData.read(buf, length, hasSplit);
        out.packet.setReliability(reliability);
        out.packet.setOrderChannel(orderChannel);

        return out;
    }

    public static Frame create(FrameData packet) {
        if (packet.getReliability().isOrdered) {
            throw new IllegalArgumentException("Must provided indices for ordered data.");
        }
        final Frame out = createRaw();
        out.packet = packet.retain();
        return out;
    }

    public static Frame createOrdered(FrameData packet, int orderIndex, int sequenceIndex) {
        if (!packet.getReliability().isOrdered) {
            throw new IllegalArgumentException("No indices needed for non-ordered data.");
        }
        final Frame out = createRaw();
        out.packet = packet.retain();
        out.orderIndex = orderIndex;
        out.sequenceIndex = sequenceIndex;
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Frame createRaw() {
        final Frame out = recycler.get();
        assert out.refCnt() == 0;
        assert out.tracker == null;
        assert out.packet == null;
        assert out.promise == null;
        out.hasSplit = false;
        out.reliableIndex = out.sequenceIndex = out.orderIndex =
                out.splitCount = out.splitID = out.splitIndex = 0;
        out.setRefCnt(1);
        out.tracker = leakDetector.track(out);
        return out;
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        if (tracker != null) {
            tracker.record(hint);
        }
        packet.touch(hint);
        return this;
    }

    public Frame completeFragment(ByteBuf fullData) {
        assert packet.isFragment();
        final Frame out = createRaw();
        out.reliableIndex = reliableIndex;
        out.sequenceIndex = sequenceIndex;
        out.orderIndex = orderIndex;
        out.packet = FrameData.read(fullData, fullData.readableBytes(), false);
        out.packet.setOrderChannel(getOrderChannel());
        out.packet.setReliability(getReliability());
        return out;
    }

    public int fragment(int splitID, int splitSize, int reliableIndex, List<Object> outList) {
        final ByteBuf data = packet.createData();
        try {
            final int dataSplitSize = splitSize - HEADER_SIZE;
            final int splitCountTotal =
                    (data.readableBytes() + dataSplitSize - 1) / dataSplitSize; //round up
            for (int splitIndexIterator = 0; splitIndexIterator < splitCountTotal;
                    splitIndexIterator++) {
                final int length = Math.min(dataSplitSize, data.readableBytes());
                final Frame out = createRaw();
                out.reliableIndex = reliableIndex;
                out.sequenceIndex = sequenceIndex;
                out.orderIndex = orderIndex;
                out.splitCount = splitCountTotal;
                out.splitID = splitID;
                out.splitIndex = splitIndexIterator;
                out.hasSplit = true;
                out.packet = FrameData.read(data, length, true);
                out.packet.setOrderChannel(getOrderChannel());
                out.packet.setReliability(getReliability().makeReliable()); //reliable form only
                assert out.packet.isFragment();
                if (out.getRoughPacketSize() > splitSize) {
                    throw new IllegalStateException("mtu fragment mismatch");
                }
                reliableIndex = UINT.B3.plus(reliableIndex, 1);
                outList.add(out);
            }
            assert !data.isReadable();
            return splitCountTotal;
        } finally {
            data.release();
        }
    }

    public ByteBuf retainedFragmentData() {
        assert packet.isFragment();
        return packet.createData();
    }

    @Override
    public Frame retain() {
        return (Frame) super.retain();
    }

    @Override
    protected void deallocate() {
        if (packet != null) {
            packet.release();
            packet = null;
        }
        if (tracker != null) {
            tracker.close(this);
            tracker = null;
        }
        promise = null;
        handle.recycle(this);
    }

    public FrameData retainedFrameData() {
        return packet.retain();
    }

    public void write(ByteBuf out) {
        writeHeader(out);
        packet.write(out);
    }

    protected void writeHeader(ByteBuf out) {
        out.writeByte((getReliability().code() << 5) | (hasSplit ? SPLIT_FLAG : 0));
        out.writeShort(packet.getDataSize() * Byte.SIZE);

        assert !(hasSplit && !getReliability().isReliable);

        if (getReliability().isReliable) {
            out.writeMediumLE(reliableIndex);
        }
        if (getReliability().isSequenced) {
            out.writeMediumLE(sequenceIndex);
        }
        if (getReliability().isOrdered) {
            out.writeMediumLE(orderIndex);
            out.writeByte(getOrderChannel());
        }
        if (hasSplit) {
            out.writeInt(splitCount);
            out.writeShort(splitID);
            out.writeInt(splitIndex);
        }
    }

    public FramedPacket.Reliability getReliability() {
        return packet.getReliability();
    }

    public int getSequenceIndex() {
        return sequenceIndex;
    }

    public int getOrderChannel() {
        return packet.getOrderChannel();
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public boolean hasSplit() {
        return hasSplit;
    }

    public int getSplitId() {
        return splitID;
    }

    public int getSplitIndex() {
        return splitIndex;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public int getDataSize() {
        return packet.getDataSize();
    }

    public int getRoughPacketSize() {
        return getDataSize() + HEADER_SIZE;
    }

    public void setReliableIndex(int reliableIndex) {
        this.reliableIndex = reliableIndex;
    }

    public ChannelPromise getPromise() {
        return promise;
    }

    public void setPromise(ChannelPromise promise) {
        this.promise = promise;
    }

    protected static final class FrameComparator implements java.util.Comparator<Frame> {
        public int compare(Frame a, Frame b) {
            if (a == b) {
                return 0;
            } else if (!a.getReliability().isReliable) {
                return -1;
            } else if (!b.getReliability().isReliable) {
                return 1;
            }
            return UINT.B3.minusWrap(a.reliableIndex, b.reliableIndex) < 0 ? -1 : 1;
        }
    }

}
