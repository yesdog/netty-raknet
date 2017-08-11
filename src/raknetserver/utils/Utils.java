package raknetserver.utils;

import io.netty.buffer.ByteBuf;

public class Utils {

	public static byte[] readBytes(ByteBuf from) {
		return readBytes(from, from.readableBytes());
	}

	public static byte[] readBytes(ByteBuf from, int length) {
		byte[] data = new byte[length];
		from.readBytes(data);
		return data;
	}

	public static int getSplitCount(int length, int maxlength) {
		int count = length / maxlength;
		if ((length % maxlength) != 0) {
			count++;
		}
		return count;
	}

	public static int divideAndCeilWithBase(int number, int base) {
		int fp = number / base;
		int m = number % base;
		if (m == 0) {
			return fp;
		} else {
			return fp + 1;
		}
	}

}
