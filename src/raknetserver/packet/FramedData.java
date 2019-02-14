package raknetserver.packet;

import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import raknetserver.frame.Frame;
import raknetserver.utils.Constants;

public final class FramedData extends AbstractReferenceCounted implements Packet {

	protected static final byte[] FIBONACCI = new byte[] { 1, 1, 2, 3, 5, 8, 13, 21 };

	private static final ResourceLeakDetector leakDetector =
			ResourceLeakDetectorFactory.instance().newResourceLeakDetector(FramedData.class);
	private static final Recycler<FramedData> recycler = new Recycler<FramedData>() {
		@Override
		protected FramedData newObject(Handle<FramedData> handle) {
			return new FramedData(handle);
		}
	};

	@SuppressWarnings("unchecked")
	public static FramedData create() {
		final FramedData out = recycler.get();
		assert out.refCnt() == 0;
		assert out.tracker == null;
		out.seqId = out.resendTicks = out.sendAttempts = 0;
		out.setRefCnt(1);
		out.tracker = leakDetector.track(out);
		return out;
	}

	public static FramedData read(ByteBuf buf) {
		final FramedData out = create();
		buf.skipBytes(1);
		out.seqId = buf.readUnsignedMediumLE();
		while (buf.isReadable()) {
			out.frames.add(Frame.read(buf));
		}
		return out;
	}

	protected final ArrayList<Frame> frames = new ArrayList<>(8);
	protected final Recycler.Handle<FramedData> handle;
	protected int seqId;
	protected int resendTicks;
	protected int sendAttempts;
	protected ResourceLeakTracker<FramedData> tracker;

	private FramedData(Recycler.Handle<FramedData> handle) {
		this.handle = handle;
		setRefCnt(0);
	}

	public ByteBuf createData(ByteBufAllocator alloc) {
		final ByteBuf header = alloc.ioBuffer(4);
		final CompositeByteBuf out = alloc.compositeDirectBuffer(1 + frames.size());
		header.writeByte(Packets.FRAME_DATA_START);
		header.writeMediumLE(seqId);
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

	public void refreshResend(int scale) {
		if (sendAttempts > 0) { //remove unreliable on resend
			frames.removeIf(frame -> {
				if (!frame.getReliability().isReliable) {
					frame.release();
					return true;
				}
				return false;
			});
		}
		//TODO: what happens if its empty now?
		resendTicks = FIBONACCI[Math.min(sendAttempts++, FIBONACCI.length - 1)] * scale + Constants.RETRY_TICK_OFFSET;
	}

	public void scheduleResend() {
		resendTicks = 0; //resend asap
	}

	public boolean resendTick(int nTicks) {
		resendTicks -= nTicks;
		return resendTicks <= 0; //returns true if resend needed
	}

	public int getSendAttempts() {
		return sendAttempts;
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
		packet.retain();
	}

	public void readTo(ChannelHandlerContext ctx) {
		frames.forEach(data -> ctx.fireChannelRead(data.retain()));
	}

	public int getRoughPacketSize() {
		int out = 3;
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
