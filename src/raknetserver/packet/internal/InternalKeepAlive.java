package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;

public class InternalKeepAlive implements InternalPacket {

	private long keepAlive;

	public InternalKeepAlive() {
	}

	public InternalKeepAlive(long keepAlive) {
		this.keepAlive = keepAlive;
	}

	@Override
	public void decode(ByteBuf buf) {
		keepAlive = buf.readLong();
	}

	@Override
	public void encode(ByteBuf buf) {
		buf.writeLong(keepAlive);
	}

	public long getKeepAlive() {
		return keepAlive;
	}

	public static class InternalPing extends InternalKeepAlive {}

	public static class InternalPong extends InternalKeepAlive {
		public InternalPong(long keepAlive) {
			super(keepAlive);
		}
	}

}
