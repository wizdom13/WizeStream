/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import android.media.audiofx.AudioEffect;

import androidx.annotation.NonNull;

import java.util.Arrays;

final class AndroidEqualizerEngineFactory implements EqualizerEngineFactory {
    @Override
    public boolean isAvailable() {
        try {
            final AudioEffect.Descriptor[] effects = AudioEffect.queryEffects();
            return effects != null && Arrays.stream(effects)
                    .anyMatch(effect -> AudioEffect.EFFECT_TYPE_EQUALIZER.equals(effect.type));
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    @NonNull
    @Override
    public EqualizerEngine create(final int audioSessionId) {
        return new AndroidEqualizerEngine(audioSessionId);
    }
}
