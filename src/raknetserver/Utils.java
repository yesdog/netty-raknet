package raknetserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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


	public static List<int[]> splitArray(int[] array, int limit) {
		List<int[]> list = new ArrayList<int[]>();
		if (array.length <= limit) {
			list.add(array);
			return list;
		}
		int count = getSplitCount(array.length, limit);
		int copied = 0;
		for (int i = 0; i < count; i++) {
			list.add(Arrays.copyOfRange(array, copied, Math.min(array.length, copied + limit)));
			copied += limit;
		}
		return list;
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
