package raknetserver.packet;

import io.netty.buffer.ByteBuf;

public class Disconnect extends SimpleFramedPacket {

	protected Reliability reliability = Reliability.RELIABLE;

	@Override
	public void decode(ByteBuf buf) {
	}

	@Override
	public void encode(ByteBuf buf) {
	}

}
