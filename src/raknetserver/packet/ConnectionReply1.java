package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;

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
		buf.writeBytes(Constants.MAGIC);
		buf.writeLong(serverId);
		buf.writeBoolean(hasSecurity);
		buf.writeShort(mtu);
	}

}
