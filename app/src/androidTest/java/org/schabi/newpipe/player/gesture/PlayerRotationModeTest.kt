package org.schabi.newpipe.player.gesture

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.player.helper.PlayerRotationMode

class PlayerRotationModeTest {
    @Test
    fun fixedModeLocksOnlyTheExpandedPlayerAndReleasesOnCollapse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.player_rotation_mode_key)
        val previous = preferences.getString(key, null)
        try {
            preferences.edit().putString(key, "fixed").commit()
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val sheet = View(activity)
                    val controller = LockedOrientationFullscreenController()
                    controller.attach(sheet, BottomSheetBehavior.STATE_EXPANDED)
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, activity.requestedOrientation)
                    // Re-expanding must not undo a manual fullscreen orientation request.
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    controller.onPlayerSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED)
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.requestedOrientation)
                    controller.onPlayerSheetStateChanged(BottomSheetBehavior.STATE_COLLAPSED)
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
                    preferences.edit().putString(key, "system").commit()
                    controller.onPlayerSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED)
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
                    assertEquals(PlayerRotationMode.SYSTEM, PlayerRotationMode.get(activity))
                }
            }
        } finally {
            preferences.edit().putString(key, previous).commit()
        }
    }
}
