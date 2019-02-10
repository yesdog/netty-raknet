package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalPing extends AbstractInternalPacket {

	protected long timestamp;
	protected Reliability reliability = Reliability.UNRELIABLE_SEQUENCED;

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