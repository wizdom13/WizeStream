package org.schabi.newpipe.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding

class BottomPlayerControlsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bottomLayoutFitsPortraitLandscapeAndRtlAndRestoresTheOriginalControls() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.bottom_player_controls_key)
        val previous = prefs.all[key] as? Boolean
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                listOf(Triple(360, 800, false), Triple(640, 360, false), Triple(360, 640, true)).forEach { (width, height, rtl) ->
                    val themed = ContextThemeWrapper(context, R.style.DarkTheme)
                    val binding = PlayerBinding.inflate(LayoutInflater.from(themed))
                    binding.root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    binding.playbackControlRoot.visibility = View.VISIBLE
                    binding.metadataView.visibility = View.VISIBLE
                    binding.titleTextView.text = "A lesson worth coming back to"
                    binding.channelTextView.text = "WizeStream · Learning and discovery"
                    binding.qualityTextView.text = "1080p"
                    binding.playbackSpeed.text = "1×"
                    binding.resizeTextView.text = "FIT"
                    binding.captionTextView.text = "CC"
                    binding.playbackCurrentTime.text = "3:24"
                    binding.playbackEndTime.text = "12:48"
                    binding.playbackSeekBar.progress = 27
                    binding.queueButton.visibility = View.VISIBLE
                    binding.commentsButton.visibility = View.VISIBLE
                    binding.segmentsButton.visibility = View.VISIBLE
                    binding.screenRotationButton.visibility = View.VISIBLE
                    val originalParents = listOf(binding.metadataView, binding.primaryControls, binding.secondaryControls, binding.bottomControls, binding.playPauseButton)
                        .associateWith { it.parent }
                    val originalParams = binding.playPauseButton.layoutParams
                    val originalTitleSize = binding.titleTextView.textSize
                    val controller = BottomPlayerControls(binding)
                    var clicks = 0
                    binding.playPauseButton.setOnClickListener { clicks++ }
                    prefs.edit().putBoolean(key, false).commit()
                    assertFalse(controller.update(true, 12, 24, 12, 24))
                    prefs.edit().putBoolean(key, true).commit()
                    assertFalse(controller.update(false, 12, 24, 12, 24))
                    repeat(3) {
                        assertTrue(controller.update(true, dp(12), dp(24), dp(12), dp(24)))
                        measure(binding.root, dp(width), dp(height))
                        val panel = binding.root.findViewById<View>(R.id.bottom_player_controls_panel)
                        assertNotNull(panel)
                        val title = bounds(binding, binding.metadataView)
                        val transport = bounds(binding, binding.playPauseButton)
                        val timeline = bounds(binding, binding.bottomControls)
                        val secondary = bounds(binding, binding.secondaryControls)
                        assertTrue("Metadata must be above the transport row", title.bottom <= transport.top)
                        assertTrue("Transport must be above the timeline", transport.bottom <= timeline.top)
                        assertTrue("Timeline must be above secondary actions", timeline.bottom <= secondary.top)
                        assertTrue("Controls must clear the top inset", title.top >= dp(24))
                        assertTrue("Actions must clear the bottom inset", secondary.bottom <= dp(height - 24))
                        assertTrue("Timeline must fit the viewport", timeline.left >= 0 && timeline.right <= dp(width))
                        assertEquals(R.id.playbackSeekBar, binding.commentsButton.nextFocusDownId)
                        assertEquals(R.id.resizeTextView, binding.playbackSeekBar.nextFocusDownId)
                        assertEquals(R.id.playbackSeekBar, binding.resizeTextView.nextFocusUpId)
                        val actionScroll = binding.primaryControls.parent.parent as HorizontalScrollView
                        if (width <= 360) {
                            assertTrue("Narrow screens must allow scrolling through all actions", actionScroll.getChildAt(0).width > actionScroll.width)
                        }
                        binding.playPauseButton.performClick()
                        if (it == 0) savePreview(binding, "$width-$height${if (rtl) "-rtl" else ""}")
                        assertFalse(controller.update(false, 12, 24, 12, 24))
                        controller.applyInsets(false, 12, 24, 12, 24)
                        assertEquals(24, binding.topControls.paddingTop)
                        assertEquals(12, binding.bottomControls.paddingLeft)
                        assertEquals(24, binding.bottomControls.paddingBottom)
                        originalParents.forEach { (view, parent) -> assertSame(parent, view.parent) }
                        assertSame(originalParams, binding.playPauseButton.layoutParams)
                        assertEquals(originalTitleSize, binding.titleTextView.textSize, 0.01f)
                        assertNull(binding.root.findViewById<View>(R.id.bottom_player_controls_panel))
                    }
                    assertEquals(3, clicks)
                }
            }
        } finally {
            prefs.edit().apply { previous?.let { putBoolean(key, it) } ?: remove(key) }.commit()
        }
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun bounds(binding: PlayerBinding, view: View): Rect = Rect(0, 0, view.width, view.height).also {
        (binding.root as ViewGroup).offsetDescendantRectToMyCoords(view, it)
    }

    private fun savePreview(binding: PlayerBinding, name: String) {
        val bitmap = Bitmap.createBitmap(binding.root.width, binding.root.height, Bitmap.Config.ARGB_8888)
        binding.root.draw(Canvas(bitmap))
        val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
