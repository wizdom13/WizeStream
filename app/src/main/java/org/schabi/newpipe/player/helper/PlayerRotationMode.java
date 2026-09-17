package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/** Player rotation policy; the legacy boolean continues to work in restored backups. */
public enum PlayerRotationMode {
    FIXED("fixed"), SYSTEM("system"), SENSORS("sensors");

    private final String value;

    PlayerRotationMode(final String value) {
        this.value = value;
    }

    public static PlayerRotationMode get(final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return resolve(preferences.getString(context.getString(R.string.player_rotation_mode_key),
                        null),
                preferences.getBoolean(context.getString(R.string.rotate_to_fullscreen_key), true));
    }

    public static PlayerRotationMode resolve(final String value, final boolean legacyEnabled) {
        for (final PlayerRotationMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return legacyEnabled ? SENSORS : SYSTEM;
    }

    public static void initializePreference(final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.player_rotation_mode_key);
        if (!preferences.contains(key)) {
            preferences.edit().putString(key, get(context).value).apply();
        }
    }
}
