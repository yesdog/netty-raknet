package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalDisconnect extends AbstractInternalPacket {

	protected Reliability reliability = Reliability.RELIABLE;

	@Override
	public void decode(ByteBuf buf) {
	}

	@Override
	public void encode(ByteBuf buf) {
	}

}
