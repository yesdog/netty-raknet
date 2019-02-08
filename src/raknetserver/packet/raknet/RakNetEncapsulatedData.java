package raknetserver.packet.raknet;

import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.utils.Constants;

public class RakNetEncapsulatedData extends AbstractReferenceCounted implements RakNetPacket {

	protected static final byte[] FIBONACCI = new byte[] { 1, 1, 2, 3, 5, 8, 13, 21 }; //used for retry backoff

	private int seqId;
	private int resendTicks = 0;
	private int sendAttempts = 0;
	private long sentTime = -1;
	private final ArrayList<EncapsulatedPacket> packets = new ArrayList<>(8);

	@Override
	public void decode(ByteBuf buf) {
		assert packets.isEmpty();
		seqId = buf.readUnsignedMediumLE();
		while (buf.isReadable()) {
			EncapsulatedPacket packet = new EncapsulatedPacket();
			packet.decode(buf);
			packets.add(packet);
		}
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeMediumLE(seqId);
		for (EncapsulatedPacket packet : packets) {
			packet.encode(buf);
		}
	}

	@Override
	protected void deallocate() {
		packets.forEach(packet -> packet.release());
		packets.clear();
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		return this;
	}

	@Override
	public void finalize() throws Throwable {
		if (!packets.isEmpty()) {
			System.err.println("RakNetEncapsulatedData data leak");
		}
		super.finalize();
	}

	public void refreshResend(int scale) {
		if (sentTime == -1) {
			sentTime = System.nanoTime(); //only set on first attempt
		}
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

	public long timeSinceSend() {
		return System.nanoTime() - sentTime;
	}

	public int getSeqId() {
		return seqId;
	}

	public void setSeqId(int seqId) {
		this.seqId = seqId;
	}

	public ArrayList<EncapsulatedPacket> getPackets() {
		return packets;
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
