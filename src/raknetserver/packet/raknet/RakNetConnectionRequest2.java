package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetDataSerializer;

public class RakNetConnectionRequest2 implements RakNetPacket {

	private int mtu;
	private long guid;

	@Override
	public void decode(ByteBuf buf) {
		buf.skipBytes(RakNetConstants.MAGIC.length);
		RakNetDataSerializer.readAddress(buf);
		mtu = buf.readShort();
		guid = buf.readLong();
	}

	@Override
	public void encode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	public int getMtu() {
		return mtu;
	}

	public long getGUID() {
		return guid;
	}

}
