package org.schabi.newpipe.player.pip;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NativePipControllerTest {
    private static final float TOLERANCE = 0.001f;

    @Test
    public void invalidAspectRatioFallsBackToWidescreen() {
        assertEquals(16f / 9f, NativePipController.sanitizeAspectRatio(Float.NaN), TOLERANCE);
        assertEquals(16f / 9f, NativePipController.sanitizeAspectRatio(0f), TOLERANCE);
    }

    @Test
    public void extremeAspectRatiosAreClampedToAndroidLimits() {
        assertEquals(1f / 2.39f, NativePipController.sanitizeAspectRatio(0.1f), TOLERANCE);
        assertEquals(2.39f, NativePipController.sanitizeAspectRatio(5f), TOLERANCE);
    }

    @Test
    public void normalVideoAspectRatiosArePreserved() {
        assertEquals(16f / 9f, NativePipController.sanitizeAspectRatio(16f / 9f), TOLERANCE);
        assertEquals(9f / 16f, NativePipController.sanitizeAspectRatio(9f / 16f), TOLERANCE);
    }
}
