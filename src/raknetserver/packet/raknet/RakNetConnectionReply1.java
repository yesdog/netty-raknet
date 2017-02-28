package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;

public class RakNetConnectionReply1 implements RakNetPacket {

	private static final boolean hasSecurity = false;

	private final int mtu;

	public RakNetConnectionReply1(int mtu) {
		this.mtu = mtu;
	}

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeBytes(RakNetConstants.MAGIC);
		buf.writeLong(RakNetConstants.SERVER_ID);
		buf.writeBoolean(hasSecurity);
		buf.writeShort(mtu);
	}

}
