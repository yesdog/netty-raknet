package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.utils.DataSerializer;

public class ConnectionFailed extends SimplePacket implements Packet {

	@Override
	public void decode(ByteBuf buf) {
	}

	@Override
	public void encode(ByteBuf buf) {
		DataSerializer.writeMagic(buf);
		buf.writeLong(0);
	}

}
