package raknetserver.packet;

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;
import raknetserver.utils.DataSerializer;

public class ServerHandshake extends SimpleFramedPacket {

	private InetSocketAddress clientAddr;
	private long timestamp;
	protected Reliability reliability = Reliability.RELIABLE;

	public ServerHandshake() {

	}

	public ServerHandshake(InetSocketAddress clientAddr, long timestamp) {
		this.clientAddr = clientAddr;
		this.timestamp = timestamp;
	}

	@Override
	public void decode(ByteBuf buf) {
		//TODO: real decode
		buf.skipBytes(buf.readableBytes());
	}

	@Override
	public void encode(ByteBuf buf) {
		DataSerializer.writeAddress(buf, clientAddr);
		buf.writeShort(0);
		for (int i = 0; i < 20; i++) {
			DataSerializer.writeAddress(buf, Constants.NULL_ADDR);
		}
		buf.writeLong(timestamp);
		buf.writeLong(System.currentTimeMillis());
	}

}
