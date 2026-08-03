package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackParameterPreferencesTest {
    private static final float FLOAT_TOLERANCE = 0.001f;

    @Test
    public void allDialogAdjustmentStepsAreSupported() {
        final float[] adjustmentSteps = {
                PlaybackParameterPreferences.STEP_1_PERCENT,
                PlaybackParameterPreferences.STEP_5_PERCENT,
                PlaybackParameterPreferences.STEP_10_PERCENT,
                PlaybackParameterPreferences.STEP_25_PERCENT,
                PlaybackParameterPreferences.STEP_100_PERCENT
        };

        for (final float adjustmentStep : adjustmentSteps) {
            assertEquals(adjustmentStep,
                    PlaybackParameterPreferences.sanitizeAdjustmentStep(adjustmentStep),
                    FLOAT_TOLERANCE);
        }
    }

    @Test
    public void unsupportedAdjustmentStepUsesSharedDefault() {
        assertEquals(PlaybackParameterPreferences.DEFAULT_ADJUSTMENT_STEP,
                PlaybackParameterPreferences.sanitizeAdjustmentStep(0.50f),
                FLOAT_TOLERANCE);
    }
}
