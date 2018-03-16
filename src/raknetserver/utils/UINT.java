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

		public static final int MAX_VALUE = ((1 << (Byte.SIZE * 3)) - 1);

		public static int plus(int value, int add) {
			return (value + add) & MAX_VALUE;
		}

		public static int minus(int value, int minus) {
			return (value - minus) & MAX_VALUE;
		}

	}

}
