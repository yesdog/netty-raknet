package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;

public class UnconnectedPing extends SimplePacket implements Packet {

	private long clientTime;

	@Override
	public void decode(ByteBuf buf) {
		this.clientTime = buf.readLong();
		buf.skipBytes(Constants.MAGIC.length);
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
