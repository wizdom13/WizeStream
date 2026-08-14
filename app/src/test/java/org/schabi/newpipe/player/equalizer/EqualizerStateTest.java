package org.schabi.newpipe.player.equalizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EqualizerStateTest {
    @Test
    public void fixedPresetsHavePortableTenBandCurves() {
        for (final EqualizerPreset preset : EqualizerPreset.values()) {
            if (preset == EqualizerPreset.CUSTOM) {
                continue;
            }
            final int[] gains = preset.getGains();
            assertEquals(EqualizerState.BAND_COUNT, gains.length);
            for (final int gain : gains) {
                assertTrue(gain >= EqualizerState.MIN_GAIN_STEP);
                assertTrue(gain <= EqualizerState.MAX_GAIN_STEP);
            }
        }
    }

    @Test
    public void editingABandCreatesTheSingleCustomCurveAndClampsGain() {
        final EqualizerState edited = EqualizerState.flat()
                .withEnabled(true)
                .withBandGain(2, EqualizerState.MAX_GAIN_STEP + 10);

        assertEquals(EqualizerPreset.CUSTOM, edited.getPreset());
        assertEquals(EqualizerState.MAX_GAIN_STEP, edited.getGains()[2]);
        assertEquals(EqualizerState.BAND_COUNT, edited.getGains().length);
    }

    @Test
    public void selectingPresetReplacesTheWholeCurve() {
        final EqualizerState state = EqualizerState.flat()
                .withBandGain(0, 8)
                .withPreset(EqualizerPreset.ROCK);

        assertEquals(EqualizerPreset.ROCK, state.getPreset());
        assertArrayEquals(EqualizerPreset.ROCK.getGains(), state.getGains());
    }

    @Test
    public void positiveBoostCreatesMatchingAutomaticHeadroom() {
        final int[] gains = EqualizerPreset.FLAT.getGains();
        gains[5] = 12;
        final EqualizerState state =
                new EqualizerState(true, EqualizerPreset.CUSTOM, gains);

        assertEquals(0.501187f, state.getHeadroomMultiplier(), 0.00001f);
        assertEquals(1.0f, state.withEnabled(false).getHeadroomMultiplier(), 0.0f);
    }
}
