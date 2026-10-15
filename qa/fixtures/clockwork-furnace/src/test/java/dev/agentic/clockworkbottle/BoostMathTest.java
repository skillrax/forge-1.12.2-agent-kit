package dev.agentic.clockworkbottle;
import org.junit.Test;
import static org.junit.Assert.*;
public final class BoostMathTest {
    @Test public void independentDoublingAndCost() {
        int multiplier=0;
        for(int[] expected:new int[][]{{2,30},{4,60},{8,120},{16,240}}) {
            multiplier=BoostMath.nextMultiplier(multiplier);
            assertEquals(expected[0],multiplier);assertEquals(expected[1],BoostMath.costSeconds(multiplier));
        }
    }
    @Test public void maximumAffordableRequestAndOverflow() {
        assertEquals(16384,BoostMath.nextMultiplier(8192));
        assertEquals(245760,BoostMath.costSeconds(16384));
        assertEquals(-1,ChargeMath.spend(4320000,245760));
        assertEquals(-1,BoostMath.nextMultiplier(Integer.MAX_VALUE));
        assertEquals(-1,BoostMath.nextMultiplier(-1));assertEquals(-1,BoostMath.nextMultiplier(3));
    }
    @Test public void reservationsPreservePromisedRates() {
        assertTrue(BoostMath.fitsBudget(8192,3,4));
        assertFalse(BoostMath.fitsBudget(8192,0,2));
        assertTrue(BoostMath.fitsBudget(1,1,8192));
        assertFalse(BoostMath.fitsBudget(1,1,16384));
        assertFalse(BoostMath.fitsBudget(Long.MAX_VALUE,0,4));
    }
}
