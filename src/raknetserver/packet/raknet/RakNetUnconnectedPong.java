package raknetserver.packet.raknet;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetDataSerializer;

public class RakNetUnconnectedPong implements RakNetPacket {

	private final long clientTime;
	private final String info;

	public RakNetUnconnectedPong(long clientTime, String info) {
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
		buf.writeLong(RakNetConstants.SERVER_ID);
		buf.writeBytes(RakNetConstants.MAGIC);
		RakNetDataSerializer.writeString(buf, info);
	}

}
