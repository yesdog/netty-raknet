package raknetserver.packet.impl;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.InternalPacket;

public class InternalConnectionRequest implements InternalPacket {

	private long timestamp;

	@Override
	public void decode(ByteBuf buf) {
		buf.skipBytes(8); //client id
		timestamp = buf.readLong();
		buf.skipBytes(1); //use security
	}

	@Override
	public void encode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	public long getTimeStamp() {
		return timestamp;
	}

}
