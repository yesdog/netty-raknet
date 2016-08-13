package raknetserver.packet.impl;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetPacket;

public class RakNetInvalidVersion implements RakNetPacket {

	public static final int VALID_VERSION = 8;

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeByte(VALID_VERSION);
		buf.writeBytes(RakNetConstants.MAGIC);
		buf.writeLong(RakNetConstants.SERVER_ID);
	}

}
