package dev.agentic.clockworkbottle;

import java.nio.FloatBuffer;
import org.junit.Test;
import org.lwjgl.BufferChecks;
import static org.junit.Assert.*;

public class BoostLabelsTest {
    @Test public void colorBufferMeetsLwjglGetFloatContract() {
        FloatBuffer color = BoostLabels.createColorBuffer();
        // Exercise the same LWJGL guard that rejected the original four-float allocation.
        BufferChecks.checkBuffer(color, 16);
        color.put(0, .2F); color.put(1, .4F); color.put(2, .6F); color.put(3, 1F);
        assertEquals(.2F, color.get(0), 0F);
        assertEquals(.4F, color.get(1), 0F);
        assertEquals(.6F, color.get(2), 0F);
        assertEquals(1F, color.get(3), 0F);
    }
}
