package raknetserver.utils;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;
import static raknetserver.utils.UINT.B3.*;

public class UINTTests {
    @Test
    public void testMinusWrap() {
        Random random = new Random(0);
        for(int i = 0 ; i < 1000000 ; i++) {
            int a = (int)(random.nextDouble() * MAX_VALUE);
            int d = (int)(random.nextDouble() * MAX_VALUE) - HALF_MAX;
            int b = plus(a, d);

            assertEquals(minusWrap(b, a), d);
        }
    }

    @Test
    public void testMinusFail() {
        Random random = new Random(0);
        boolean hasFailed = false;
        for(int i = 0 ; i < 1000000 ; i++) {
            int a = (int)(random.nextDouble() * MAX_VALUE);
            int d = (int)(random.nextDouble() * MAX_VALUE) - HALF_MAX;
            int b = plus(a, d);

            if (minus(b, a) != d) {
                hasFailed = true;
            }
        }

        assertTrue(hasFailed);
    }
}
