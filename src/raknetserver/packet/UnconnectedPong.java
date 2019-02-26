package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;
import raknetserver.utils.DataSerializer;

public class UnconnectedPong extends SimplePacket implements Packet {

	private final long clientTime;
	private final long serverId;
	private final String info;

	public UnconnectedPong(long clientTime, long serverId, String info) {
		this.clientTime = clientTime;
		this.serverId = serverId;
		this.info = info;
	}

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeLong(clientTime);
		buf.writeLong(serverId);
		buf.writeBytes(Constants.MAGIC);
		DataSerializer.writeString(buf, info);
	}

}
