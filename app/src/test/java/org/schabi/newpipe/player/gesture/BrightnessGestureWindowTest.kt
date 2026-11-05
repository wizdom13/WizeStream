package org.schabi.newpipe.player.gesture

import android.app.Activity
import android.app.Application
import android.provider.Settings
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlayerHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BrightnessGestureWindowTest {
    @Test
    fun `first swipe starts at system setting and stores actual brightness across mode changes`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        try {
            Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 102)
            assertEquals(-1f, activity.window.attributes.screenBrightness, 0f)
            BrightnessGesture.apply(activity, 1f, 100)
            assertEquals(0.41f, activity.window.attributes.screenBrightness, 0.00001f)
            prefs.edit().putBoolean(activity.getString(R.string.perceptual_brightness_key), true).commit()
            BrightnessGesture.apply(activity, 0f, 100)
            assertEquals(0.41f, activity.window.attributes.screenBrightness, 0.00001f)
            BrightnessGesture.apply(activity, -1f, 100)
            val actual = activity.window.attributes.screenBrightness
            assertEquals(actual, PlayerHelper.getScreenBrightness(activity), 0f)
            prefs.edit().putBoolean(activity.getString(R.string.perceptual_brightness_key), false).commit()
            BrightnessGesture.apply(activity, 0f, 100)
            assertEquals(actual, activity.window.attributes.screenBrightness, 0f)
            assertEquals(102, Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        } finally {
            prefs.edit().clear().commit()
            controller.pause().stop().destroy()
        }
    }
}
