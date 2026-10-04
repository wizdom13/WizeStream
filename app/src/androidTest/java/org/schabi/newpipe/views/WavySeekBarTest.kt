package org.schabi.newpipe.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity

class WavySeekBarTest {
    @Test
    fun waveUsesTheSelectedTintAndItsPhaseChangesTheRenderedCurve() {
        val track = WavySeekBar.WaveTrack(1f)
        track.setBounds(0, 0, 120, 14)
        track.setTintList(ColorStateList.valueOf(Color.RED))
        fun pixels(): IntArray {
            val bitmap = Bitmap.createBitmap(120, 14, Bitmap.Config.ARGB_8888)
            track.draw(Canvas(bitmap))
            return IntArray(120 * 14).also {
                bitmap.getPixels(it, 0, 120, 0, 0, 120, 14)
                bitmap.recycle()
            }
        }
        val first = pixels()
        assertTrue(first.any { Color.alpha(it) > 200 && Color.red(it) > 200 && Color.green(it) == 0 })
        track.phase = 1.5f
        assertTrue(first.zip(pixels()).count { (a, b) -> a != b } > 100)
    }

    @Test
    fun switchingStylesKeepsNativeProgressBufferAndThumb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val old = preferences.getString(WavySeekBar.STYLE_KEY, null)
        preferences.edit().putString(WavySeekBar.STYLE_KEY, "standard").commit()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var bar: WavySeekBar
                scenario.onActivity { activity ->
                    bar = WavySeekBar(activity)
                    bar.max = 100
                    bar.progress = 40
                    bar.secondaryProgress = 70
                    activity.setContentView(bar)
                }
                val original = bar.progressDrawable
                val thumb = bar.thumb
                preferences.edit().putString(WavySeekBar.STYLE_KEY, "wavy").commit()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    assertNotSame(original, bar.progressDrawable)
                    assertSame(thumb, bar.thumb)
                    assertEquals(40, bar.progress)
                    assertEquals(70, bar.secondaryProgress)
                    bar.playbackActive = true
                    bar.visibility = View.GONE
                    bar.playbackActive = false
                }
                preferences.edit().putString(WavySeekBar.STYLE_KEY, "standard").commit()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    assertSame(original, bar.progressDrawable)
                    assertEquals(40, bar.progress)
                }
            }
        } finally {
            preferences.edit().apply {
                if (old == null) remove(WavySeekBar.STYLE_KEY) else putString(WavySeekBar.STYLE_KEY, old)
            }.commit()
        }
    }
}
