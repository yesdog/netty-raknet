package raknetserver.packet.internal;

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;
import raknetserver.packet.RakNetConstants;
import raknetserver.packet.RakNetDataSerializer;

public class InternalServerHandshake implements InternalPacket {

	private final InetSocketAddress clientAddr;
	private final long timestamp;

	public InternalServerHandshake(InetSocketAddress clientAddr, long timestamp) {
		this.clientAddr = clientAddr;
		this.timestamp = timestamp;
	}

	@Override
	public void decode(ByteBuf buf) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void encode(ByteBuf buf) {
		RakNetDataSerializer.writeAddress(buf, clientAddr);
		buf.writeShort(0);
		for (int i = 0; i < 10; i++) {
			RakNetDataSerializer.writeAddress(buf, RakNetConstants.NULL_ADDR);
		}
		buf.writeLong(timestamp);
		buf.writeLong(System.currentTimeMillis());
	}

}
