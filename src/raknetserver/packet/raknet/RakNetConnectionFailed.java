package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;

public class RakNetConnectionFailed implements RakNetPacket {

	@Override
	public void decode(ByteBuf buf) {
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeBytes(RakNetConstants.MAGIC);
		buf.writeLong(0);
	}

}
