package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public interface InternalPacket {

	void decode(ByteBuf buf);

	void encode(ByteBuf buf);

}
