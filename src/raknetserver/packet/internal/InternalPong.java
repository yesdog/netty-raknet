package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalPong implements InternalPacket{

	private long pingTimestamp;
	private long pongTimestamp;

	public InternalPong() {
	}

	public InternalPong(long pingTimestamp) {
		this.pingTimestamp = pingTimestamp;
		this.pongTimestamp = System.currentTimeMillis();
	}

	@Override
	public void decode(ByteBuf buf) {
		pingTimestamp = buf.readLong();
		pongTimestamp = buf.readLong();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeLong(pingTimestamp);
	}

	public long getPingTimestamp() {
		return pingTimestamp;
	}

	public long getPongTimestamp() {
		return pongTimestamp;
	}

}