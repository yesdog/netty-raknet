package raknetserver.packet;

import io.netty.buffer.ByteBuf;

public class Ping extends SimpleFramedPacket {

	protected long timestamp;
	protected Reliability reliability = Reliability.UNRELIABLE;

	public Ping() {
		timestamp = System.currentTimeMillis();
	}

	@Override
	public void decode(ByteBuf buf) {
		timestamp = buf.readLong();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeLong(timestamp);
	}

	public long getTimestamp() {
		return timestamp;
	}

}