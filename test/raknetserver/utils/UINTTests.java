package raknetserver.utils;

import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
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

    @Test
    public void testCompareIterator() {
        Random random = new Random(0);
        IntSortedSet testSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
        for(int i = 0 ; i < 500000 ; i++) {
            int a = (int)(random.nextDouble() * MAX_VALUE);
            int b = (int)(random.nextDouble() * MAX_VALUE);
            testSet.add(plus(a, b));
        }
        int x = testSet.firstInt();
        IntBidirectionalIterator itr = testSet.iterator();
        while (itr.hasNext()) {
            int next = itr.nextInt();
            int d = minusWrap(x, next);
            assertTrue(d <= 0);
            x = next;
        }
    }
}
