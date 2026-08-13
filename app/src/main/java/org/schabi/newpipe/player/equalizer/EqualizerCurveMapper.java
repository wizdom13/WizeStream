/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import androidx.annotation.NonNull;

/**
 * Maps WizeStream's fixed curve to an Android device's native bands.
 */
public final class EqualizerCurveMapper {
    private static final int MILLIBELS_PER_HALF_DECIBEL = 50;

    private EqualizerCurveMapper() {
    }

    @NonNull
    public static short[] mapToDeviceBands(@NonNull final int[] centerFrequenciesMilliHertz,
                                           final short minimumMillibels,
                                           final short maximumMillibels,
                                           @NonNull final int[] canonicalGainSteps) {
        if (canonicalGainSteps.length != EqualizerState.BAND_COUNT) {
            throw new IllegalArgumentException("Canonical equalizer curve must have ten bands");
        }
        final short[] levels = new short[centerFrequenciesMilliHertz.length];
        for (int index = 0; index < centerFrequenciesMilliHertz.length; index++) {
            final double frequencyHertz = centerFrequenciesMilliHertz[index] / 1_000.0;
            final double gainStep = interpolateGainStep(frequencyHertz, canonicalGainSteps);
            final int millibels = (int) Math.round(gainStep * MILLIBELS_PER_HALF_DECIBEL);
            levels[index] = (short) Math.max(
                    minimumMillibels,
                    Math.min(maximumMillibels, millibels));
        }
        return levels;
    }

    static double interpolateGainStep(final double frequencyHertz,
                                      @NonNull final int[] gains) {
        final int[] frequencies = EqualizerState.BAND_FREQUENCIES_HZ;
        if (frequencyHertz <= frequencies[0]) {
            return gains[0];
        }
        if (frequencyHertz >= frequencies[frequencies.length - 1]) {
            return gains[gains.length - 1];
        }
        for (int upper = 1; upper < frequencies.length; upper++) {
            if (frequencyHertz <= frequencies[upper]) {
                final int lower = upper - 1;
                final double logarithmicLower = Math.log(frequencies[lower]);
                final double logarithmicUpper = Math.log(frequencies[upper]);
                final double position = (Math.log(frequencyHertz) - logarithmicLower)
                        / (logarithmicUpper - logarithmicLower);
                return gains[lower] + position * (gains[upper] - gains[lower]);
            }
        }
        return gains[gains.length - 1];
    }
}
