package org.schabi.newpipe.player.video

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class VideoAdjustmentEditorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun preferencesAreOptInAndForgettingRemovesPreviouslySavedValues() {
        val prefs = context.getSharedPreferences("video-adjustments-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = VideoAdjustmentPreferences(prefs)
            assertEquals(VideoAdjustmentState(), store.load())
            val state = VideoAdjustmentState(true, -20, 35, 150, true)
            store.save(state)
            assertEquals(state, VideoAdjustmentPreferences(prefs).load())
            store.save(state.copy(remember = false))
            assertTrue(prefs.all.isEmpty())
            assertEquals(VideoAdjustmentState(), store.load())
            prefs.edit().putString("video_adjustments_remember_v1", "corrupt").commit()
            assertEquals(VideoAdjustmentState(), store.load())
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun editorResetsLiveWithoutClosingAndWorksInLightDarkAndBlackThemes() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            for (theme in listOf(R.style.LightTheme, R.style.DarkTheme, R.style.BlackTheme)) {
                var dialog: AlertDialog? = null
                try {
                    scenario.onActivity { activity ->
                        val controller = VideoAdjustmentController(
                            VideoAdjustmentState(true, -15, 20, 160),
                            {},
                            {},
                            {}
                        )
                        val shown = VideoAdjustmentDialog.show(ContextThemeWrapper(activity, theme), controller)
                        dialog = shown
                        assertEquals(-15f, shown.findViewById<Slider>(R.id.brightness_slider)!!.value)
                        shown.findViewById<SwitchMaterial>(R.id.adjustments_remember)!!.performClick()
                        assertTrue(controller.state.remember)
                        shown.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                        assertTrue(shown.isShowing)
                        assertEquals(VideoAdjustmentState(enabled = true, remember = true), controller.state)
                        assertEquals(0f, shown.findViewById<Slider>(R.id.contrast_slider)!!.value)
                        assertEquals(100f, shown.findViewById<Slider>(R.id.saturation_slider)!!.value)
                        shown.findViewById<SwitchMaterial>(R.id.adjustments_enabled)!!.performClick()
                        assertFalse(controller.state.enabled)
                        assertFalse(shown.findViewById<Slider>(R.id.brightness_slider)!!.isEnabled)
                        shown.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity { assertFalse(dialog!!.isShowing) }
                } finally {
                    scenario.onActivity { dialog?.dismiss() }
                }
            }
        }
    }
}
