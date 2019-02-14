package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;

public class ConnectionReply1 extends SimplePacket implements Packet {

	private static final boolean hasSecurity = false;

	private final int mtu;

	public ConnectionReply1(int mtu) {
		this.mtu = mtu;
	}

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeBytes(Constants.MAGIC);
		buf.writeLong(Constants.SERVER_ID);
		buf.writeBoolean(hasSecurity);
		buf.writeShort(mtu);
	}

}
