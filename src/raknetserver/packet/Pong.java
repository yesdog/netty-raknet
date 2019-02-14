package raknetserver.packet;

import io.netty.buffer.ByteBuf;

public class Pong extends SimpleFramedPacket {

	private long pingTimestamp;
	private long pongTimestamp;

	protected Reliability reliability = Reliability.UNRELIABLE;

	public Pong() {

	}

	public Pong(long pingTimestamp, Reliability reliability) {
		this(pingTimestamp);
		this.reliability = reliability;
	}

	public Pong(long pingTimestamp) {
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