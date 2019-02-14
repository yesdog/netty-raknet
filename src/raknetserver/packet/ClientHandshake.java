package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import raknetserver.utils.DataSerializer;

public class ClientHandshake extends SimpleFramedPacket {

	protected Reliability reliability = Reliability.RELIABLE_ORDERED;

	@Override
	public void decode(ByteBuf buf) {
		for (int i = 0; i < 21; i++) {
			DataSerializer.readAddress(buf);
		}
		buf.skipBytes(8); //pong time
		buf.skipBytes(8); //timestamp
	}

	@Override
	public void encode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

}
