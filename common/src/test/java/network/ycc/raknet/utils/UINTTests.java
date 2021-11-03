package network.ycc.raknet.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

public class UINTTests {
    @Test
    public void testMinusWrap() {
        Random random = new Random(0);
        for (int i = 0; i < 1000000; i++) {
            int a = (int) (random.nextDouble() * UINT.B3.MAX_VALUE);
            int d = (int) (random.nextDouble() * UINT.B3.MAX_VALUE) - UINT.B3.HALF_MAX;
            int b = UINT.B3.plus(a, d);

            Assertions.assertEquals(UINT.B3.minusWrap(b, a), d);
        }
    }

    @Test
    public void testMinusFail() {
        Random random = new Random(0);
        boolean hasFailed = false;
        for (int i = 0; i < 1000000; i++) {
            int a = (int) (random.nextDouble() * UINT.B3.MAX_VALUE);
            int d = (int) (random.nextDouble() * UINT.B3.MAX_VALUE) - UINT.B3.HALF_MAX;
            int b = UINT.B3.plus(a, d);

            if (UINT.B3.minus(b, a) != d) {
                hasFailed = true;
            }
        }

        Assertions.assertTrue(hasFailed);
    }

    @Test
    public void testCompareIterator() {
        Random random = new Random(0);
        IntSortedSet testSet = new IntRBTreeSet(UINT.B3.COMPARATOR);
        for (int i = 0; i < 500000; i++) {
            int a = (int) (random.nextDouble() * UINT.B3.MAX_VALUE);
            int b = (int) (random.nextDouble() * UINT.B3.MAX_VALUE);
            testSet.add(UINT.B3.plus(a, b));
        }
        int x = testSet.firstInt();
        IntBidirectionalIterator itr = testSet.iterator();
        while (itr.hasNext()) {
            int next = itr.nextInt();
            int d = UINT.B3.minusWrap(x, next);
            Assertions.assertTrue(d <= 0);
            x = next;
        }
    }
}
