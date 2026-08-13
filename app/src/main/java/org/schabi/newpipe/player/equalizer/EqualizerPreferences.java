/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Versioned local persistence for the Release 1 equalizer state.
 */
public final class EqualizerPreferences implements EqualizerStateStore {
    public static final String PREFERENCE_KEY = "equalizer_state_v1";

    @NonNull
    private final SharedPreferences preferences;

    public EqualizerPreferences(@NonNull final Context context) {
        this(PreferenceManager.getDefaultSharedPreferences(context));
    }

    EqualizerPreferences(@NonNull final SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @NonNull
    @Override
    public EqualizerState load() {
        final String stored = preferences.getString(PREFERENCE_KEY, null);
        if (stored == null) {
            return EqualizerState.flat();
        }
        try {
            final JSONObject object = new JSONObject(stored);
            if (object.getInt("version") != EqualizerState.VERSION) {
                return EqualizerState.flat();
            }
            final JSONArray array = object.getJSONArray("gains");
            if (array.length() != EqualizerState.BAND_COUNT) {
                return EqualizerState.flat();
            }
            final int[] gains = new int[EqualizerState.BAND_COUNT];
            for (int index = 0; index < gains.length; index++) {
                gains[index] = array.getInt(index);
                if (gains[index] < EqualizerState.MIN_GAIN_STEP
                        || gains[index] > EqualizerState.MAX_GAIN_STEP) {
                    return EqualizerState.flat();
                }
            }
            return new EqualizerState(
                    object.getBoolean("enabled"),
                    EqualizerPreset.fromId(object.getString("selectedPreset")),
                    gains);
        } catch (final JSONException | ClassCastException ignored) {
            return EqualizerState.flat();
        }
    }

    @Override
    public void save(@NonNull final EqualizerState state) {
        final JSONObject object = new JSONObject();
        final JSONArray gains = new JSONArray();
        for (final int gain : state.getGains()) {
            gains.put(gain);
        }
        try {
            object.put("version", EqualizerState.VERSION);
            object.put("enabled", state.isEnabled());
            object.put("selectedPreset", state.getPreset().getId());
            object.put("gains", gains);
            preferences.edit().putString(PREFERENCE_KEY, object.toString()).apply();
        } catch (final JSONException error) {
            throw new IllegalStateException("Could not serialize equalizer state", error);
        }
    }
}
