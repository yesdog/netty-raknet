package raknet.packet;

import io.netty.buffer.ByteBuf;

import raknet.utils.DataSerializer;

public class ConnectionReply1 extends SimplePacket implements Packet {

	private static final boolean hasSecurity = false;

	private final int mtu;
	private final long serverId;

	public ConnectionReply1(int mtu, long serverId) {
		this.mtu = mtu;
		this.serverId = serverId;
	}

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		DataSerializer.writeMagic(buf);
		buf.writeLong(serverId);
		buf.writeBoolean(hasSecurity);
		buf.writeShort(mtu);
	}

}
