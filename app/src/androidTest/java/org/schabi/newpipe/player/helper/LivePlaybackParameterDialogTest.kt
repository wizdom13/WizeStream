package org.schabi.newpipe.player.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class LivePlaybackParameterDialogTest {
    @Test
    fun narrowSelectionRowsKeepEveryLabelInsideItsButton() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = PlaybackParameterDialog.newInstance(1.0, 1.0, false, true) { _, _, _ -> }
                fragment.showNow(activity.supportFragmentManager, "choice-labels")
                val dialog = fragment.requireDialog() as AlertDialog
                dialog.findViewById<View>(R.id.pitchToogleControlModes)!!.performClick()
                val rows = listOf(R.id.stepSizeChoices, R.id.pitchControlModeTabs)
                rows.forEach { id ->
                    val row = dialog.findViewById<ViewGroup>(id)!!
                    val width = (280 * activity.resources.displayMetrics.density).toInt()
                    val maxHeight = (200 * activity.resources.displayMetrics.density).toInt()
                    row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
                    val height = row.measuredHeight
                    row.layout(0, 0, width, height)
                    for (i in 0 until row.childCount) {
                        val button = row.getChildAt(i) as? Chip ?: continue
                        val layout = requireNotNull(button.layout)
                        assertTrue(button.text.isNotBlank())
                        assertEquals(1, layout.lineCount)
                        assertTrue("Label ${button.text} exceeds width ${button.width}, padding ${button.compoundPaddingLeft}/${button.compoundPaddingRight}, text size ${button.textSize}", button.paint.measureText(button.text.toString()) <= button.width - button.compoundPaddingLeft - button.compoundPaddingRight)
                        assertTrue("Label extends above button", button.baseline + button.paint.fontMetrics.top >= 0)
                        assertTrue("Label extends below button", button.baseline + button.paint.fontMetrics.bottom <= button.height)
                    }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    row.draw(Canvas(bitmap))
                    val folder = File(activity.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
                    File(folder, "playback-choices-$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                fragment.dismissNow()
            }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val folder = File(context.getExternalFilesDir(null), "player-layout-previews")
            val destination = "/sdcard/Download/wizestream-player-layout-previews"
            val commands = listOf("mkdir -p $destination") + folder.listFiles().orEmpty().filter { it.name.startsWith("playback-choices-") }.map { "cp ${it.absolutePath} $destination/" }
            commands.forEach { command ->
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
            }
        }
    }

    @Test
    fun materialControlsPreserveStepChangesAndCancel() {
        var speed = 1.2f
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = PlaybackParameterDialog.newInstance(1.2, 1.0, false, true) { tempo, _, _ ->
                    speed = tempo
                }
                fragment.showNow(activity.supportFragmentManager, "material-parameters")
                val dialog = fragment.requireDialog() as AlertDialog
                val slider = requireNotNull(dialog.findViewById<Slider>(R.id.tempoSeekbar))
                assertEquals(10000f, slider.valueTo, 0f)
                val step = requireNotNull(dialog.findViewById<Chip>(R.id.stepSizeTwentyFivePercent))
                step.performClick()
                assertTrue(step.isChecked)
                assertFalse(dialog.findViewById<Chip>(R.id.stepSizeTenPercent)!!.isChecked)
                dialog.findViewById<View>(R.id.tempoStepUp)!!.performClick()
                assertEquals(1.45f, speed, 0.001f)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            scenario.onActivity { activity ->
                assertEquals(1.2f, speed, 0.001f)
                activity.supportFragmentManager.executePendingTransactions()
            }
        }
    }

    @Test
    fun livePlaybackExplainsWhySkippingIsUnavailable() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = PlaybackParameterDialog.newInstance(1.0, 1.0, true, false) { _, _, _ -> }
                fragment.showNow(activity.supportFragmentManager, "live-parameters")
                val dialog = fragment.requireDialog() as AlertDialog
                val checkbox = requireNotNull(dialog.findViewById<MaterialCheckBox>(R.id.skipSilenceCheckbox))
                assertEquals(View.GONE, dialog.findViewById<View>(R.id.channelSkipButton)!!.visibility)
                assertFalse(checkbox.isEnabled)
                assertFalse(checkbox.isChecked)
                assertEquals(activity.getString(R.string.skip_silence_unavailable_live), checkbox.text.toString())
                fragment.dismissNow()
            }
        }
    }

    @Test
    fun channelSkippingIsAccessibleFromTheSpeedDialog() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var opened = false
                var parametersApplied = false
                val fragment = PlaybackParameterDialog.newInstance(1.0, 1.0, false, true) { _, _, _ ->
                    parametersApplied = true
                }.withChannelSkippingAction { opened = true }
                fragment.showNow(activity.supportFragmentManager, "channel-skip-parameters")
                val dialog = fragment.requireDialog() as AlertDialog
                val button = requireNotNull(dialog.findViewById<View>(R.id.channelSkipButton))
                assertEquals(View.VISIBLE, button.visibility)
                assertTrue(button.performClick())
                assertTrue(opened)
                assertTrue(parametersApplied)
                assertFalse(dialog.isShowing)
                activity.supportFragmentManager.executePendingTransactions()
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
