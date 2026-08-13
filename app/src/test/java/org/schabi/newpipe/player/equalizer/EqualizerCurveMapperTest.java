package org.schabi.newpipe.player.equalizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EqualizerCurveMapperTest {
    @Test
    public void canonicalCentersMapToExactNativeLevels() {
        final int[] centersMilliHertz = new int[EqualizerState.BAND_COUNT];
        final int[] gains = new int[EqualizerState.BAND_COUNT];
        final short[] expected = new short[EqualizerState.BAND_COUNT];
        for (int index = 0; index < EqualizerState.BAND_COUNT; index++) {
            centersMilliHertz[index] =
                    EqualizerState.BAND_FREQUENCIES_HZ[index] * 1_000;
            gains[index] = index - 5;
            expected[index] = (short) (gains[index] * 50);
        }

        assertArrayEquals(expected, EqualizerCurveMapper.mapToDeviceBands(
                centersMilliHertz, (short) -1_500, (short) 1_500, gains));
    }

    @Test
    public void interpolationUsesLogFrequencySpacing() {
        final int[] gains = EqualizerPreset.FLAT.getGains();
        gains[5] = 0;
        gains[6] = 10;

        final double logarithmicMidpoint = Math.sqrt(1_000.0 * 2_000.0);
        assertEquals(5.0,
                EqualizerCurveMapper.interpolateGainStep(logarithmicMidpoint, gains),
                0.00001);
    }

    @Test
    public void mappedLevelsRespectVendorRange() {
        final int[] gains = new int[EqualizerState.BAND_COUNT];
        java.util.Arrays.fill(gains, EqualizerState.MAX_GAIN_STEP);

        assertArrayEquals(new short[]{300}, EqualizerCurveMapper.mapToDeviceBands(
                new int[]{1_000_000}, (short) -300, (short) 300, gains));
    }
}
