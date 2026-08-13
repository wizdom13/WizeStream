/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Portable ten-band equalizer state. Gains are stored in half-decibel units.
 */
public final class EqualizerState {
    public static final int VERSION = 1;
    public static final int BAND_COUNT = 10;
    public static final int MIN_GAIN_STEP = -24;
    public static final int MAX_GAIN_STEP = 24;
    public static final int[] BAND_FREQUENCIES_HZ = {
        32, 64, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000
    };

    private final boolean enabled;
    @NonNull
    private final EqualizerPreset preset;
    @NonNull
    private final int[] gains;

    public EqualizerState(final boolean enabled,
                          @NonNull final EqualizerPreset preset,
                          @NonNull final int[] gains) {
        if (gains.length != BAND_COUNT) {
            throw new IllegalArgumentException("An equalizer curve must contain ten bands");
        }
        this.enabled = enabled;
        this.preset = Objects.requireNonNull(preset);
        this.gains = Arrays.stream(gains)
                .map(EqualizerState::clampGain)
                .toArray();
    }

    @NonNull
    public static EqualizerState flat() {
        return new EqualizerState(false, EqualizerPreset.FLAT, EqualizerPreset.FLAT.getGains());
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    public EqualizerPreset getPreset() {
        return preset;
    }

    @NonNull
    public int[] getGains() {
        return Arrays.copyOf(gains, gains.length);
    }

    @NonNull
    public EqualizerState withEnabled(final boolean newEnabled) {
        return new EqualizerState(newEnabled, preset, gains);
    }

    @NonNull
    public EqualizerState withPreset(@NonNull final EqualizerPreset newPreset) {
        if (newPreset == EqualizerPreset.CUSTOM) {
            return new EqualizerState(enabled, EqualizerPreset.CUSTOM, gains);
        }
        return new EqualizerState(enabled, newPreset, newPreset.getGains());
    }

    @NonNull
    public EqualizerState withBandGain(final int band, final int gainStep) {
        if (band < 0 || band >= BAND_COUNT) {
            throw new IllegalArgumentException("Equalizer band index is outside the curve");
        }
        final int[] updated = getGains();
        updated[band] = clampGain(gainStep);
        return new EqualizerState(enabled, EqualizerPreset.CUSTOM, updated);
    }

    public float getHeadroomMultiplier() {
        final int maximumGainStep = Arrays.stream(gains).max().orElse(0);
        if (!enabled || maximumGainStep <= 0) {
            return 1.0f;
        }
        final double maximumGainDecibels = maximumGainStep / 2.0;
        return (float) Math.pow(10.0, -maximumGainDecibels / 20.0);
    }

    public static int clampGain(final int gainStep) {
        return Math.max(MIN_GAIN_STEP, Math.min(MAX_GAIN_STEP, gainStep));
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EqualizerState)) {
            return false;
        }
        final EqualizerState that = (EqualizerState) other;
        return enabled == that.enabled
                && preset == that.preset
                && Arrays.equals(gains, that.gains);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(enabled, preset) + Arrays.hashCode(gains);
    }
}
