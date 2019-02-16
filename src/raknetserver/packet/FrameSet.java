package raknetserver.packet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import raknetserver.frame.Frame;

public final class FrameSet extends AbstractReferenceCounted implements Packet {

	public static final int HEADER_SIZE = 4;

	private static final ResourceLeakDetector leakDetector =
			ResourceLeakDetectorFactory.instance().newResourceLeakDetector(FrameSet.class);
	private static final Recycler<FrameSet> recycler = new Recycler<FrameSet>() {
		@Override
		protected FrameSet newObject(Handle<FrameSet> handle) {
			return new FrameSet(handle);
		}
	};

	@SuppressWarnings("unchecked")
	public static FrameSet create() {
		final FrameSet out = recycler.get();
		assert out.refCnt() == 0;
		assert out.tracker == null;
		out.seqId = out.pendingTicks = 0;
		out.tracker = leakDetector.track(out);
		out.setRefCnt(1);
		return out;
	}

	public static FrameSet read(ByteBuf buf) {
		final FrameSet out = create();
		buf.skipBytes(1);
		out.seqId = buf.readUnsignedMediumLE();
		while (buf.isReadable()) {
			out.frames.add(Frame.read(buf));
		}
		return out;
	}

	protected final ArrayList<Frame> frames = new ArrayList<>(8);
	protected final Recycler.Handle<FrameSet> handle;
	protected int seqId;
	protected int pendingTicks;
	protected ResourceLeakTracker<FrameSet> tracker;

	private FrameSet(Recycler.Handle<FrameSet> handle) {
		this.handle = handle;
		setRefCnt(0);
	}

	public ByteBuf createData(ByteBufAllocator alloc) {
		final ByteBuf header = alloc.ioBuffer(4);
		final CompositeByteBuf out = alloc.compositeDirectBuffer(1 + frames.size());
		header.writeByte(Packets.FRAME_DATA_START);
		header.writeMediumLE(seqId);
		assert header.readableBytes() <= HEADER_SIZE;
		out.addComponent(true, header);
		frames.forEach(frame -> out.addComponent(true, frame.createData(alloc)));
		return out;
	}

	protected void deallocate() {
		frames.forEach(Frame::release);
		frames.clear();
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
		frames.forEach(packet -> packet.touch(hint));
		return this;
	}

	public int incrTick(int nTicks) {
		pendingTicks += nTicks;
		return pendingTicks;
	}

	public int getSeqId() {
		return seqId;
	}

	public void setSeqId(int seqId) {
		this.seqId = seqId;
	}

	public int getNumPackets() {
		return frames.size();
	}

	public void addPacket(Frame packet) {
		frames.add(packet);
	}

	public void createFrames(Consumer<Frame> consumer) {
		frames.forEach(frame -> consumer.accept(frame.retain()));
	}

	public int getRoughSize() {
		int out = HEADER_SIZE;
		for (Frame packet : frames) {
			out += packet.getRoughPacketSize();
		}
		return out;
	}

	public boolean isEmpty() {
		return frames.isEmpty();
	}

	@Override
	public String toString() {
		return String.format("FramedData(frames: %s, seq: %s)", frames.size(), getSeqId());
	}

}
