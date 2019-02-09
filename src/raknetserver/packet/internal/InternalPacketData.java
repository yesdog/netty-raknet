package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

public final class InternalPacketData extends AbstractReferenceCounted implements InternalPacket {

	private static final ResourceLeakDetector leakDetector =
			ResourceLeakDetectorFactory.instance().newResourceLeakDetector(InternalPacketData.class);

	public static InternalPacketData read(ByteBuf buf, int length) {
		assert length > 0;
		final InternalPacketData data = new InternalPacketData();
		try {
			final int packetId = buf.readUnsignedByte();
			data.decode(buf.readSlice(length - 1));
			data.packetId = packetId;
			assert data.getDataSize() == length;
			return data;
		} catch (Throwable t) {
			data.release();
			throw t;
		}
	}

	public static InternalPacketData read(ByteBuf buf) {
		return read(buf, buf.readableBytes());
	}

	public static InternalPacketData readFragment(ByteBuf buf, int length) {
		assert length > 0;
		final InternalPacketData data = new InternalPacketData();
		try {
			data.decode(buf.readSlice(length));
			data.packetId = 0xFF;
			data.fragment = true;
			assert data.getDataSize() == length;
			return data;
		} catch (Throwable t) {
			data.release();
			throw t;
		}
	}

	public static InternalPacketData createEmpty(int packetId) {
		final InternalPacketData data = new InternalPacketData();
		data.packetId = packetId;
		return data;
	}

	public static InternalPacketData createRead(int packetId, ByteBuf buf) {
		final InternalPacketData data = new InternalPacketData();
		try {
			data.decode(buf);
			data.packetId = packetId;
			return data;
		} catch (Throwable t) {
			data.release();
			throw t;
		}
	}

	//TODO: reset
	private int packetId = 0;
	private int orderId = 0;
	private boolean fragment = false;
	private ByteBuf data = null;
	private ResourceLeakTracker<InternalPacketData> tracker = null;
	private Reliability reliability = Reliability.RELIABLE_ORDERED;

	public InternalPacketData() {
		createTracker();
	}

	public ByteBuf retainedData() {
		return data.retainedDuplicate();
	}

	public void decode(ByteBuf buf) {
		assert data == null;
		data = buf.readRetainedSlice(buf.readableBytes());
	}

	public void encode(ByteBuf buf) {
		data.markReaderIndex();
		buf.writeBytes(data);
		data.resetReaderIndex();
	}

	public void encodeFull(ByteBuf buf) {
		assert !fragment;
		buf.writeByte(getPacketId());
		encode(buf);
	}

	public InternalPacketData toInternalPacketData(ByteBufAllocator alloc) {
		return this.retain();
	}

	@Override
	public InternalPacketData retain() {
		return (InternalPacketData) super.retain();
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
		packetId = 0;
	}

	public ReferenceCounted touch(Object hint) {
		if (tracker != null) {
			tracker.record(hint);
		}
		return this;
	}

	public int getPacketId() {
		assert !fragment;
		return packetId;
	}

	public Reliability getReliability() {
		return reliability;
	}

	public void setReliability(Reliability reliability) {
		this.reliability = reliability;
	}

	public int getOrderId() {
		return orderId;
	}

	public void setOrderId(int orderId) {
		this.orderId = orderId;
	}

	public int getDataSize() {
		return data.readableBytes() + (fragment ? 0 : 1);
	}

	public boolean isFragment() {
		return fragment;
	}

	@SuppressWarnings("unchecked")
	protected void createTracker() {
		assert tracker == null;
		tracker = leakDetector.track(this);
	}

}
