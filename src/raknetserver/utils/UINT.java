package raknetserver.utils;

import it.unimi.dsi.fastutil.ints.IntComparator;

public class UINT {

	public static class B2 {

		public static final int MAX_VALUE = ((1 << (Byte.SIZE * 2)) - 1);

		public static int plus(int value, int add) {
			return (value + add) & MAX_VALUE;
		}

		public static int minus(int value, int minus) {
			return (value - minus) & MAX_VALUE;
		}

	}

	public static class B3 {

		public static final int MAX_VALUE = (1 << (Byte.SIZE * 3)) - 1;
		public static final int HALF_MAX = MAX_VALUE / 2;
		public static final IntComparator COMPARATOR = new Comparator();

		public static int plus(int value, int add) {
			return (value + add) & MAX_VALUE;
		}

		public static int minus(int value, int minus) {
			return (value - minus) & MAX_VALUE;
		}

		public static int minusWrap(int value, int minus) {
			final int dist = value - minus;
			if (dist < 0) {
				return -minusWrap(minus, value);
			} else if (dist > HALF_MAX) {
				return value - (minus + MAX_VALUE + 1);
			} else {
				return dist;
			}
		}

		protected static class Comparator implements IntComparator {
			@Override
			public final int compare(final int a, final int b) {
				final int d = UINT.B3.minusWrap(a, b);
				return d < 0 ? -1 : ((d == 0) ? 0 : 1);
			}
		}

	}

}
