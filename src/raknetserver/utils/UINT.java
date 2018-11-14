package raknetserver.utils;

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

		public static final int ADD_MASK = 1 << (Byte.SIZE * 3);
		public static final int MAX_VALUE = ADD_MASK - 1;
		public static final int HALF_MAX = MAX_VALUE / 2;

		public static int plus(int value, int add) {
			return (value + add) & MAX_VALUE;
		}

		public static int minus(int value, int minus) {
			return (value - minus) & MAX_VALUE;
		}

		public static int minusWrap(int value, int minus) {
			if (minus == value) {
				return 0;
			} else if (minus > value) {
				return -minusWrap(minus, value);
			}
			assert minus < value;
			int dist = value - minus;
			if (dist > HALF_MAX) {
				return value - (minus | ADD_MASK);
			}

			return dist;
		}

		static {
			for(int i = 0 ; i < 100000 ; i++) {
				int a = (int)(Math.random() * MAX_VALUE);
				int d = (int)(Math.random() * MAX_VALUE) - HALF_MAX;
				int b = plus(a, d);

				if(minusWrap(b, a) != d)
					System.err.println(String.format("self test failed for (%d, %d) expected %d got %d", a, b, d, minusWrap(a, b)));
			}

			System.err.println("self test done");
		}
	}

}
