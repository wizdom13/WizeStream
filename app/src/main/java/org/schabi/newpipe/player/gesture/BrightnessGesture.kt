package org.schabi.newpipe.player.gesture

import android.app.Activity
import android.provider.Settings
import androidx.preference.PreferenceManager
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlayerHelper

/** Window-only brightness control; saved values always remain in linear brightness space. */
internal object BrightnessGesture {
    // Hybrid log-gamma constants, as used by AOSP SettingsLib BrightnessUtils.
    // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/packages/SettingsLib/src/com/android/settingslib/display/BrightnessUtils.java
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f

    fun toBrightness(position: Float, perceptual: Boolean): Float {
        val value = bounded(position)
        if (!perceptual || value == 0f || value == 1f) return value
        return bounded(if (value <= 0.5f) value * value / 3f else (exp((value - C) / A) + B) / 12f)
    }

    fun toPosition(brightness: Float, perceptual: Boolean): Float {
        val value = bounded(brightness)
        if (!perceptual || value == 0f || value == 1f) return value
        return bounded(if (value <= 1f / 12f) sqrt(value * 3f) else A * ln(value * 12f - B) + C)
    }

    fun initialBrightness(windowBrightness: Float, systemBrightness: () -> Float): Float = if (windowBrightness.isFinite() && windowBrightness >= 0f) bounded(windowBrightness) else bounded(systemBrightness())

    fun adjust(brightness: Float, distance: Float, range: Int, perceptual: Boolean): Float {
        if (range <= 0 || !distance.isFinite() || distance == 0f) return bounded(brightness)
        return toBrightness(toPosition(brightness, perceptual) + distance / range, perceptual)
    }

    /** Returns the display position, or null when the gesture range is not initialized. */
    fun apply(activity: Activity, distance: Float, range: Int): Float? {
        if (range <= 0 || !distance.isFinite()) return null
        val perceptual = PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(activity.getString(R.string.perceptual_brightness_key), false)
        val window = activity.window
        val attributes = window.attributes
        val initial = initialBrightness(attributes.screenBrightness) {
            // Public API fallback. Adaptive brightness and OEM calibration may differ
            // from this stored setting, but -1 must never be interpreted as minimum.
            try {
                Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            } catch (_: SecurityException) {
                0.5f
            }
        }
        val brightness = adjust(initial, distance, range, perceptual)
        attributes.screenBrightness = brightness
        window.attributes = attributes
        PlayerHelper.setScreenBrightness(activity, brightness)
        return toPosition(brightness, perceptual)
    }

    private fun bounded(value: Float): Float = if (value.isNaN()) 0.5f else value.coerceIn(0f, 1f)
}
