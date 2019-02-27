package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.Recycler;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

public final class PacketData extends AbstractReferenceCounted implements FramedPacket {

    private static final ResourceLeakDetector leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(PacketData.class);
    private static final Recycler<PacketData> recycler = new Recycler<PacketData>() {
        @Override
        protected PacketData newObject(Handle<PacketData> handle) {
            return new PacketData(handle);
        }
    };

    @SuppressWarnings("unchecked")
    private static PacketData createRaw() {
        final PacketData out = recycler.get();
        assert out.refCnt() == 0 && out.tracker == null : "bad reuse";
        out.orderId = 0;
        out.fragment = false;
        out.data = null;
        out.reliability = Reliability.RELIABLE_ORDERED;
        out.setRefCnt(1);
        out.tracker = leakDetector.track(out);
        return out;
    }

    public static PacketData create(ByteBufAllocator alloc, int packetId, ByteBuf buf) {
        final CompositeByteBuf out = alloc.compositeDirectBuffer(2);
        try {
            out.addComponent(true, alloc.ioBuffer(1).writeByte(packetId));
            out.addComponent(true, buf.retain());
            return PacketData.read(out, out.readableBytes(), false);
        } finally {
            out.release();
        }
    }

    public static PacketData read(ByteBuf buf, int length, boolean fragment) {
        assert length > 0;
        final PacketData packet = createRaw();
        try {
            packet.data = buf.readRetainedSlice(length);
            packet.fragment = fragment;
            assert packet.getDataSize() == length;
            return packet;
        } catch (Throwable t) {
            packet.release();
            throw t;
        }
    }

    public static PacketData create(ByteBufAllocator alloc, FramedPacket packet) {
        final ByteBuf data = packet.createData(alloc);
        try {
            final PacketData out = PacketData.read(data, data.readableBytes(), false);
            out.setReliability(packet.getReliability());
            out.setOrderChannel(packet.getOrderChannel());
            return out;
        } finally {
            data.release();
        }
    }

    private final Recycler.Handle<PacketData> handle;
    private ResourceLeakTracker<PacketData> tracker;
    private int orderId;
    private boolean fragment;
    private ByteBuf data;
    private Reliability reliability;

    private PacketData(Recycler.Handle<PacketData> handle) {
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
    public PacketData retain() {
        return (PacketData) super.retain();
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

    public ByteBuf createData(ByteBufAllocator alloc) {
        return createData();
    }

    public Reliability getReliability() {
        return reliability;
    }

    public void setReliability(Reliability reliability) {
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
