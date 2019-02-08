package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;

public class InternalUserData extends AbstractReferenceCounted implements InternalPacket {

	public static InternalUserData read(ByteBuf buf, ByteBufAllocator alloc) {
		final InternalUserData data = new InternalUserData();
		final ByteBuf out = alloc.ioBuffer(buf.readableBytes());
		try {
			out.writeBytes(buf);
			data.decode(out);
			return data;
		} finally {
			out.release(); //new reference held
		}
	}

	private ByteBuf data = null;

	public InternalUserData() {

	}

	public ByteBuf retainedData() {
		return data.retainedDuplicate();
	}

	@Override
	public void decode(ByteBuf buf) {
		assert data == null;
		data = buf.readRetainedSlice(buf.readableBytes());
	}

	@Override
	public void encode(ByteBuf buf) {
		data.markReaderIndex();
		buf.writeBytes(data);
		data.resetReaderIndex();
	}

	@Override
	protected void deallocate() {
		if (data != null) {
			data.release();
			data = null;
		}
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		return this;
	}

	@Override
	public void finalize() throws Throwable {
		if (data != null) {
			System.err.println("InternalUserData data leak");
		}
		super.finalize();
	}

}
