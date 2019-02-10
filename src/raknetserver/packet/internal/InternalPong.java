package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalPong extends AbstractInternalPacket {

	private long pingTimestamp;
	private long pongTimestamp;

	protected Reliability reliability = Reliability.UNRELIABLE_SEQUENCED;

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
		buf.writeLong(pingTimestamp); //TODO: wtf is with different decode/encode?
	}

	public long getPingTimestamp() {
		return pingTimestamp;
	}

	public long getPongTimestamp() {
		return pongTimestamp;
	}

}