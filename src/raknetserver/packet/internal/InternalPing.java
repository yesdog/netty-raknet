package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalPing implements InternalPacket {

	private long timestamp;

	public InternalPing() {
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