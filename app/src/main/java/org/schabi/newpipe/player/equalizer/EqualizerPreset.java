/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * Application-owned equalizer presets. Values use half-decibel steps so they are deterministic
 * across Android devices and synchronization formats.
 */
public enum EqualizerPreset {
    FLAT("flat", new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
    BASS_BOOST("bass_boost", new int[]{10, 8, 6, 2, 0, -2, -2, 0, 2, 4}),
    VOCAL("vocal", new int[]{-4, -2, 0, 2, 4, 6, 6, 4, 2, 0}),
    ACOUSTIC("acoustic", new int[]{4, 2, 0, -2, 2, 4, 4, 2, 2, 4}),
    ROCK("rock", new int[]{6, 4, 2, 0, -2, 2, 4, 6, 6, 4}),
    CUSTOM("custom", null);

    @NonNull
    private final String id;
    @Nullable
    private final int[] gains;

    EqualizerPreset(@NonNull final String id, @Nullable final int[] gains) {
        this.id = id;
        this.gains = gains == null ? null : Arrays.copyOf(gains, gains.length);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public int[] getGains() {
        if (gains == null) {
            throw new IllegalStateException("The custom preset has no fixed curve");
        }
        return Arrays.copyOf(gains, gains.length);
    }

    @NonNull
    public static EqualizerPreset fromId(@Nullable final String id) {
        if (id != null) {
            for (final EqualizerPreset preset : values()) {
                if (preset.id.equals(id)) {
                    return preset;
                }
            }
        }
        return FLAT;
    }
}
