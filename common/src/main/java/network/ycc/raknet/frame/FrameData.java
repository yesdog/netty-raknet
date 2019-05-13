package network.ycc.raknet.frame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.Recycler;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import network.ycc.raknet.packet.FramedPacket;

public final class FrameData extends AbstractReferenceCounted implements FramedPacket {

    private static final ResourceLeakDetector<FrameData> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(FrameData.class);
    private static final Recycler<FrameData> recycler = new Recycler<FrameData>() {
        @Override
        protected FrameData newObject(Handle<FrameData> handle) {
            return new FrameData(handle);
        }
    };

    @SuppressWarnings("unchecked")
    private static FrameData createRaw() {
        final FrameData out = recycler.get();
        assert out.refCnt() == 0 && out.tracker == null : "bad reuse";
        out.orderId = 0;
        out.fragment = false;
        out.data = null;
        out.reliability = FramedPacket.Reliability.RELIABLE_ORDERED;
        out.setRefCnt(1);
        out.tracker = leakDetector.track(out);
        return out;
    }

    public static FrameData create(ByteBufAllocator alloc, int packetId, ByteBuf buf) {
        final CompositeByteBuf out = alloc.compositeDirectBuffer(2);
        try {
            out.addComponent(true, alloc.ioBuffer(1).writeByte(packetId));
            out.addComponent(true, buf.retain());
            return FrameData.read(out, out.readableBytes(), false);
        } finally {
            out.release();
        }
    }

    public static FrameData read(ByteBuf buf, int length, boolean fragment) {
        assert length > 0;
        final FrameData packet = createRaw();
        try {
            packet.data = buf.readRetainedSlice(length);
            packet.fragment = fragment;
            assert packet.getDataSize() == length;
            return packet;
        } catch (Exception t) {
            packet.release();
            throw t;
        }
    }

    private final Recycler.Handle<FrameData> handle;
    private ResourceLeakTracker<FrameData> tracker;
    private int orderId;
    private boolean fragment;
    private ByteBuf data;
    private FramedPacket.Reliability reliability;

    private FrameData(Recycler.Handle<FrameData> handle) {
        this.handle = handle;
        setRefCnt(0);
    }

    public void write(ByteBuf out) {
    	data.markReaderIndex();
    	try {
			out.writeBytes(data);
		} finally {
			data.resetReaderIndex();
		}
	}

    public ByteBuf createData() {
        return data.retainedDuplicate();
    }

    @Override
    public FrameData retain() {
        return (FrameData) super.retain();
    }

    @Override
    public String toString() {
        return String.format("PacketData(%s, length: %s, framed: %s, packetId: %s)",
                getReliability(), getDataSize(), isFragment(),
                fragment ? "n/a" : String.format("%02x", getPacketId()));
    }

    protected void deallocate() {
        if (data != null) {
            data.release();
            data = null;
        }
        if (tracker != null) {
            tracker.close(this);
            tracker = null;
        }
        handle.recycle(this);
    }

    public ReferenceCounted touch(Object hint) {
        if (tracker != null) {
            tracker.record(hint);
        }
        data.touch(hint);
        return this;
    }

    public int getPacketId() {
        assert !fragment;
        return data.getUnsignedByte(data.readerIndex());
    }

    public FramedPacket.Reliability getReliability() {
        return reliability;
    }

    public void setReliability(FramedPacket.Reliability reliability) {
        this.reliability = reliability;
    }

    public int getOrderChannel() {
        return orderId;
    }

    public void setOrderChannel(int orderChannel) {
        this.orderId = orderChannel;
    }

    public int getDataSize() {
        return data.readableBytes();
    }

    public boolean isFragment() {
        return fragment;
    }

}
