package raknet.packet;

import io.netty.buffer.ByteBuf;
import raknet.utils.DataSerializer;

public class ConnectionRequest1 extends SimplePacket implements Packet {

	private int rakNetProtocolVersion;
	private int mtu;
	@Override
	public void decode(ByteBuf buf) {
		DataSerializer.readMagic(buf);
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
