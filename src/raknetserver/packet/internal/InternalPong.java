package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalPong extends AbstractInternalPacket {

	private long pingTimestamp;
	private long pongTimestamp;

	protected Reliability reliability = Reliability.UNRELIABLE;

	public InternalPong() {

	}

	public InternalPong(long pingTimestamp, Reliability reliability) {
		this(pingTimestamp);
		this.reliability = reliability;
	}

	public InternalPong(long pingTimestamp) {
		this.pingTimestamp = pingTimestamp;
		this.pongTimestamp = System.currentTimeMillis();
	}

	@Override
	public void decode(ByteBuf buf) {
		pingTimestamp = buf.readLong();
		if (buf.isReadable()) {
			pongTimestamp = buf.readLong();
		}
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeLong(pingTimestamp);
		buf.writeLong(pongTimestamp);
	}

	public long getPingTimestamp() {
		return pingTimestamp;
	}

	public long getPongTimestamp() {
		return pongTimestamp;
	}

	public long getRTT() {
		return System.currentTimeMillis() - pingTimestamp;
	}

}