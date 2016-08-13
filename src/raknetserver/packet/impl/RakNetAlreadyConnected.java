package raknetserver.packet.impl;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetPacket;

public class RakNetAlreadyConnected implements RakNetPacket {

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeBytes(RakNetConstants.MAGIC);
		buf.writeLong(0);
	}

}
