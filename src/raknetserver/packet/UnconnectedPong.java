package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;
import raknetserver.utils.DataSerializer;

public class UnconnectedPong extends SimplePacket implements Packet {

	private final long clientTime;
	private final String info;

	public UnconnectedPong(long clientTime, String info) {
		this.clientTime = clientTime;
		this.info = info;
	}

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeLong(clientTime);
		buf.writeLong(Constants.SERVER_ID);
		buf.writeBytes(Constants.MAGIC);
		DataSerializer.writeString(buf, info);
	}

}
