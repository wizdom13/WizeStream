/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import android.media.audiofx.Equalizer;

import androidx.annotation.NonNull;

final class AndroidEqualizerEngine implements EqualizerEngine {
    @NonNull
    private final Equalizer equalizer;
    @NonNull
    private final int[] centerFrequenciesMilliHertz;
    private final short minimumMillibels;
    private final short maximumMillibels;
    private boolean released;

    AndroidEqualizerEngine(final int audioSessionId) {
        equalizer = new Equalizer(0, audioSessionId);
        final short bandCount = equalizer.getNumberOfBands();
        final short[] range = equalizer.getBandLevelRange();
        centerFrequenciesMilliHertz = new int[bandCount];
        for (short band = 0; band < bandCount; band++) {
            centerFrequenciesMilliHertz[band] = equalizer.getCenterFreq(band);
        }
        minimumMillibels = range[0];
        maximumMillibels = range[1];
    }

    @Override
    public void apply(@NonNull final int[] canonicalGainSteps) {
        ensureOpen();
        final short[] levels = EqualizerCurveMapper.mapToDeviceBands(
                centerFrequenciesMilliHertz,
                minimumMillibels,
                maximumMillibels,
                canonicalGainSteps);
        for (short band = 0; band < levels.length; band++) {
            equalizer.setBandLevel(band, levels[band]);
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        ensureOpen();
        equalizer.setEnabled(enabled);
    }

    @Override
    public void release() {
        if (!released) {
            released = true;
            equalizer.release();
        }
    }

    private void ensureOpen() {
        if (released) {
            throw new IllegalStateException("Equalizer engine has already been released");
        }
    }
}
