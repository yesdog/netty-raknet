package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;

public class RakNetConnectionRequest1 implements RakNetPacket {

	private int rakNetProtocolVersion;
	private int mtu;

	@Override
	public void decode(ByteBuf buf) {
		buf.skipBytes(RakNetConstants.MAGIC.length);
		rakNetProtocolVersion = buf.readByte();
		mtu = buf.readableBytes();
		buf.skipBytes(mtu);
	}

	@Override
	public void encode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	public int getRakNetProtocolVersion() {
		return rakNetProtocolVersion;
	}

	public int getMtu() {
		return mtu;
	}

}
