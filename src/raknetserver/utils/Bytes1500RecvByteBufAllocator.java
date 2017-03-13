package raknetserver.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;

public class Bytes1500RecvByteBufAllocator implements RecvByteBufAllocator {

	public static final Bytes1500RecvByteBufAllocator INSTANCE = new Bytes1500RecvByteBufAllocator();

	protected Bytes1500RecvByteBufAllocator() {
	}

	private static final Bytes1500RecvByteBufAllocatorHandle handle = new Bytes1500RecvByteBufAllocatorHandle();

	@Override
	public Handle newHandle() {
		return handle;
	}

	public static class Bytes1500RecvByteBufAllocatorHandle implements Handle {

		private static final int maxNormalMTU = 1500;

		protected Bytes1500RecvByteBufAllocatorHandle() {
		}
	
		@Override
		public ByteBuf allocate(ByteBufAllocator allocator) {
			return allocator.ioBuffer(maxNormalMTU);
		}

		@Override
		public int guess() {
			return maxNormalMTU;
		}

		@Override
		public void record(int realRecv) {
		}
		
	}

}
