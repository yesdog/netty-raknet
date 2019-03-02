package raknet.packet;

import io.netty.buffer.ByteBuf;

import raknet.utils.Constants;
import raknet.utils.DataSerializer;

public class UnconnectedPing extends SimplePacket implements Packet {

	private long clientTime;

	@Override
	public void decode(ByteBuf buf) {
		this.clientTime = buf.readLong();
		DataSerializer.readMagic(buf);
		buf.skipBytes(8); //guid
	}

	@Override
	public void encode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	public long getClientTime() {
		return clientTime;
	}

}
