package dev.agentic.clockworkbottle;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ChargeMathTest {
    @Test public void gameTimeCapAndExtremeInputs() {
        assertEquals(4320000,ChargeMath.boundedTicks(Long.MAX_VALUE));
        assertEquals(0,ChargeMath.boundedTicks(Long.MIN_VALUE));
        assertEquals(4320000,ChargeMath.advance(4320000));
        assertEquals(20,ChargeMath.advance(19));
    }
    @Test public void exactSpendPreservesFractionAndFailsAtomically() {
        assertEquals(19,ChargeMath.spend(319,15));
        assertEquals(-1,ChargeMath.spend(299,15));
        assertEquals(-1,ChargeMath.spend(4320000,Long.MAX_VALUE));
        assertEquals(-1,ChargeMath.spend(4320000,-1));
        assertEquals(0,ChargeMath.spend(4320000,216000));
    }
    @Test public void escalatingCostsBoundBeforeOverflow() {
        long next=15;
        for(int expected:new int[]{30,60,120,240}) {next=ChargeMath.next(next);assertEquals(expected,next);}
        assertEquals(216001,ChargeMath.next(Long.MAX_VALUE));
        assertEquals(216001,ChargeMath.next(122880));
        assertEquals(-1,ChargeMath.spend(4320000,216001));
    }
}
