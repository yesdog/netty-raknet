package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;

public interface RakNetPacket {

	public void decode(ByteBuf buf);

	public void encode(ByteBuf buf);

}
