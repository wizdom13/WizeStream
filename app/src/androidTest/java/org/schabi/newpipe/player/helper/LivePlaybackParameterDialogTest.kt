package org.schabi.newpipe.player.helper

import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import com.google.android.material.checkbox.MaterialCheckBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class LivePlaybackParameterDialogTest {
    @Test
    fun livePlaybackExplainsWhySkippingIsUnavailable() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = PlaybackParameterDialog.newInstance(1.0, 1.0, true, false) { _, _, _ -> }
                fragment.showNow(activity.supportFragmentManager, "live-parameters")
                val dialog = fragment.requireDialog() as AlertDialog
                val checkbox = requireNotNull(dialog.findViewById<MaterialCheckBox>(R.id.skipSilenceCheckbox))
                assertFalse(checkbox.isEnabled)
                assertFalse(checkbox.isChecked)
                assertEquals(activity.getString(R.string.skip_silence_unavailable_live), checkbox.text.toString())
                fragment.dismissNow()
            }
        }
    }

    @Test
    fun ordinaryPlaybackStillAllowsChangingSkipping() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = PlaybackParameterDialog.newInstance(1.0, 1.0, true, true) { _, _, _ -> }
                fragment.showNow(activity.supportFragmentManager, "vod-parameters")
                val dialog = fragment.requireDialog() as AlertDialog
                val checkbox = requireNotNull(dialog.findViewById<MaterialCheckBox>(R.id.skipSilenceCheckbox))
                assertTrue(checkbox.isEnabled)
                assertTrue(checkbox.isChecked)
                fragment.dismissNow()
            }
        }
    }
}
