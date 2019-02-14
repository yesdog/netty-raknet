package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import raknetserver.utils.DataSerializer;
import raknetserver.utils.Constants;

public class ConnectionRequest2 extends SimplePacket implements Packet {

	private int mtu;
	private long guid;

	@Override
	public void decode(ByteBuf buf) {
		buf.skipBytes(Constants.MAGIC.length);
		DataSerializer.readAddress(buf);
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
