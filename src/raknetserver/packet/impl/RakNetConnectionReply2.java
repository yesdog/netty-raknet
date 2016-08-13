package raknetserver.packet.impl;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetDataSerializer;
import raknetserver.packet.RakNetPacket;

public class RakNetConnectionReply2 implements RakNetPacket {

	private static final boolean needsSecurity = false;

	private final int mtu;
	public RakNetConnectionReply2(int mtu) {
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
		RakNetDataSerializer.writeAddress(buf, RakNetConstants.NULL_ADDR);
		buf.writeShort(mtu);
		buf.writeBoolean(needsSecurity);
	}

}
