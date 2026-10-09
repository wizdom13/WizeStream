package org.schabi.newpipe.player

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** Combines user attenuation with temporary focus, mute, equalizer and sleep-timer gains. */
internal class PlaybackVolumeController(
    context: Context,
    private val onVolumeChanged: () -> Unit,
    private val onModeChanged: () -> Unit
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val enabledKey = context.getString(R.string.independent_player_volume_key)
    private val levelKey = "independent_player_volume_level"
    private var listening = false
    private var focusMultiplier = 1f
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == enabledKey || key == levelKey) {
            if (key == enabledKey) onModeChanged()
            onVolumeChanged()
        }
    }

    val isEnabled: Boolean
        get() = preferences.getBoolean(enabledKey, false)

    var level: Float
        get() = bounded(preferences.getFloat(levelKey, 1f))
        set(value) {
            preferences.edit().putFloat(levelKey, bounded(value)).apply()
            onVolumeChanged()
        }

    fun attach() {
        if (listening) return
        focusMultiplier = 1f
        preferences.registerOnSharedPreferenceChangeListener(listener)
        listening = true
    }

    fun detach() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        listening = false
        focusMultiplier = 1f
    }

    fun setFocusMultiplier(value: Float) {
        focusMultiplier = bounded(value)
        onVolumeChanged()
    }

    fun effectiveVolume(muted: Boolean, sleepMultiplier: Float, equalizerHeadroom: Float): Float = if (muted) {
        0f
    } else {
        (if (isEnabled) level else 1f) * focusMultiplier *
            bounded(sleepMultiplier) * bounded(equalizerHeadroom)
    }

    private fun bounded(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
}
