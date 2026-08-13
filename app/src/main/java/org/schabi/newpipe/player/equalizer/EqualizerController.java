/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Owns one native equalizer engine at a time and follows the ExoPlayer audio-session lifecycle.
 */
public final class EqualizerController {
    private static final String TAG = EqualizerController.class.getSimpleName();
    private static final int NO_AUDIO_SESSION = -1;

    @NonNull
    private final EqualizerStateStore stateStore;
    @NonNull
    private final EqualizerEngineFactory engineFactory;
    @NonNull
    private EqualizerState state;
    @Nullable
    private EqualizerEngine engine;
    private int audioSessionId = NO_AUDIO_SESSION;

    public EqualizerController(@NonNull final Context context) {
        this(new EqualizerPreferences(context), new AndroidEqualizerEngineFactory());
    }

    EqualizerController(@NonNull final EqualizerStateStore stateStore,
                        @NonNull final EqualizerEngineFactory engineFactory) {
        this.stateStore = Objects.requireNonNull(stateStore);
        this.engineFactory = Objects.requireNonNull(engineFactory);
        state = stateStore.load();
    }

    @NonNull
    public EqualizerState getState() {
        return state;
    }

    public boolean isAvailable() {
        return engine != null || engineFactory.isAvailable();
    }

    public boolean isOperational() {
        return engine != null;
    }

    public void attachAudioSession(final int newAudioSessionId) {
        if (audioSessionId == newAudioSessionId && (engine != null || !state.isEnabled())) {
            return;
        }
        releaseAudioSession();
        audioSessionId = newAudioSessionId;
        ensureEngine();
    }

    public void releaseAudioSession() {
        releaseEngine();
        audioSessionId = NO_AUDIO_SESSION;
    }

    public void previewState(@NonNull final EqualizerState newState) {
        applyNewState(newState);
    }

    public void updateState(@NonNull final EqualizerState newState) {
        applyNewState(newState);
        stateStore.save(state);
    }

    private void applyNewState(@NonNull final EqualizerState newState) {
        state = Objects.requireNonNull(newState);
        if (!state.isEnabled()) {
            releaseEngine();
            return;
        }
        ensureEngine();
        applyState();
    }

    public float getHeadroomMultiplier() {
        return engine != null && state.isEnabled() ? state.getHeadroomMultiplier() : 1.0f;
    }

    private void ensureEngine() {
        if (engine != null || !state.isEnabled() || audioSessionId <= 0) {
            return;
        }
        try {
            if (!engineFactory.isAvailable()) {
                return;
            }
            engine = engineFactory.create(audioSessionId);
            applyState();
        } catch (final RuntimeException error) {
            Log.w(TAG, "Equalizer is unavailable for audio session " + audioSessionId, error);
            releaseEngine();
        }
    }

    private void applyState() {
        if (engine == null) {
            return;
        }
        try {
            engine.apply(state.getGains());
            engine.setEnabled(state.isEnabled());
        } catch (final RuntimeException error) {
            Log.w(TAG, "Could not apply equalizer state", error);
            releaseEngine();
        }
    }

    private void releaseEngine() {
        if (engine == null) {
            return;
        }
        try {
            engine.setEnabled(false);
        } catch (final RuntimeException ignored) {
            // Release below remains mandatory even when a vendor engine is already dead.
        }
        try {
            engine.release();
        } catch (final RuntimeException error) {
            Log.w(TAG, "Could not release equalizer engine cleanly", error);
        } finally {
            engine = null;
        }
    }
}
