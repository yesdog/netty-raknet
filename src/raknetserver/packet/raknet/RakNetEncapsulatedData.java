package raknetserver.packet.raknet;

import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import raknetserver.packet.EncapsulatedPacket;
import raknetserver.utils.Constants;

public class RakNetEncapsulatedData extends AbstractReferenceCounted implements RakNetPacket {

	protected static final byte[] FIBONACCI = new byte[] { 1, 1, 2, 3, 5, 8, 13, 21 }; //used for retry backoff
	private static final ResourceLeakDetector leakDetector =
			ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RakNetEncapsulatedData.class);

	protected final ArrayList<EncapsulatedPacket> packets = new ArrayList<>(8);
	protected int seqId;
	protected int resendTicks = 0;
	protected int sendAttempts = 0;
	protected long sentTime = -1;
	protected ResourceLeakTracker<RakNetEncapsulatedData> tracker = null;

	public RakNetEncapsulatedData() {
		createTracker();
	}

	@Override
	public void decode(ByteBuf buf) {
		assert packets.isEmpty();
		seqId = buf.readUnsignedMediumLE();
		while (buf.isReadable()) {
			packets.add(EncapsulatedPacket.read(buf));
		}
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeMediumLE(seqId);
		for (EncapsulatedPacket packet : packets) {
			packet.encode(buf);
			//System.out.println(packet.getReliability() + " fragment: " + packet.hasSplit());
		}
	}

	@Override
	protected void deallocate() {
		packets.forEach(packet -> packet.release());
		packets.clear();
		if (tracker != null) {
			tracker.close(this);
			tracker = null;
		}
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		if (tracker != null) {
			tracker.record(hint);
		}
		packets.forEach(packet -> packet.touch(hint));
		return this;
	}

	@SuppressWarnings("unchecked")
	protected void createTracker() {
		assert tracker == null;
		tracker = leakDetector.track(this);
	}

	public void refreshResend(int scale) {
		sentTime = System.nanoTime();
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

	public long timeSinceLastSend() {
		return System.nanoTime() - sentTime;
	}

	public int getSeqId() {
		return seqId;
	}

	public void setSeqId(int seqId) {
		this.seqId = seqId;
	}

	public int getNumPackets() {
		return packets.size();
	}

	public void addPacket(EncapsulatedPacket packet) {
		packets.add(packet);
		packet.retain();
	}

	public void readTo(ChannelHandlerContext ctx) {
		packets.forEach(data -> ctx.fireChannelRead(data.retain()));
	}

	public int getRoughPacketSize() {
		int out = 3;
		for (EncapsulatedPacket packet : packets) {
			out += packet.getRoughPacketSize();
		}
		return out;
	}

	public boolean isEmpty() {
		return packets.isEmpty();
	}

}
