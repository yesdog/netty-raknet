package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;
import raknetserver.utils.Utils;

public class InternalUserData implements InternalPacket {

	private byte[] data;

	public InternalUserData() {
	}

	public InternalUserData(byte[] data) {
		this.data = data;
	}

	@Override
	public void decode(ByteBuf buf) {
		data = Utils.readBytes(buf);
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeBytes(data);
	}

	public byte[] getData() {
		return data;
	}

}
