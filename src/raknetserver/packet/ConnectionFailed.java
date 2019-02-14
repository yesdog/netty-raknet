package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;

public class ConnectionFailed extends SimplePacket implements Packet {

	@Override
	public void decode(ByteBuf buf) {
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeBytes(Constants.MAGIC);
		buf.writeLong(0);
	}

}
