package dev.agentic.clockworkbottle;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoostSnapshotTest {
    @Test public void roundTripAndEmptyCancellation() {
        for (int size : new int[] {0, 2, 8192}) {
            long[] positions = new long[size]; int[] multipliers = new int[size], ticks = new int[size];
            for (int i = 0; i < size; i++) {positions[i] = Long.MIN_VALUE + i; multipliers[i] = 8192; ticks[i] = i % 300 + 1;}
            ByteBuf bytes = Unpooled.buffer();
            try {
                new BoostSnapshot(-1, positions, multipliers, ticks).toBytes(bytes);
                assertEquals(8 + size * 16, bytes.readableBytes());
                BoostSnapshot decoded = new BoostSnapshot(); decoded.fromBytes(bytes);
                assertEquals(-1, decoded.dimension); assertArrayEquals(positions, decoded.positions);
                assertArrayEquals(multipliers, decoded.multipliers); assertArrayEquals(ticks, decoded.ticks);
            } finally {bytes.release();}
        }
    }
    @Test public void secondsRoundUp() {
        assertEquals(15, BoostSnapshot.seconds(300)); assertEquals(15, BoostSnapshot.seconds(281));
        assertEquals(14, BoostSnapshot.seconds(280)); assertEquals(1, BoostSnapshot.seconds(1));
        assertEquals(0, BoostSnapshot.seconds(0));
    }
    @Test public void rejectMalformedPacketsBeforeAllocating() {
        for (int count : new int[] {-1, 8193, Integer.MAX_VALUE, 1}) {
            ByteBuf bytes = Unpooled.buffer();
            try {
                bytes.writeInt(0).writeInt(count);
                try {new BoostSnapshot().fromBytes(bytes); fail("Malformed payload accepted");}
                catch (IllegalArgumentException expected) {}
            } finally {bytes.release();}
        }
        try {new BoostSnapshot(0, new long[]{0}, new int[]{3}, new int[]{300}); fail();}
        catch (IllegalArgumentException expected) {}
        try {new BoostSnapshot(0, new long[]{0}, new int[]{2}, new int[]{301}); fail();}
        catch (IllegalArgumentException expected) {}
    }
}
