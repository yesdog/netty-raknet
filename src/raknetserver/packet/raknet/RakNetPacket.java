package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;

public interface RakNetPacket {

	void decode(ByteBuf buf);

	void encode(ByteBuf buf);

}
