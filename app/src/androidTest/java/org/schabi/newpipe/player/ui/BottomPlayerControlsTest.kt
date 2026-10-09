package org.schabi.newpipe.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.ParcelFileDescriptor
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
                    binding.loadingPanel.visibility = View.GONE
                    binding.metadataView.visibility = View.VISIBLE
                    binding.titleTextView.text = "A lesson worth coming back to"
                    binding.channelTextView.text = "WizeStream · Learning and discovery"
                    binding.qualityTextView.text = "1080p"
                    binding.playbackSpeed.text = "1×"
                    binding.resizeTextView.text = "FIT"
                    binding.captionTextView.contentDescription = "Captions"
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
                        assertTrue(binding.qualityTextView.isFocusable)
                        assertTrue(binding.playbackSpeed.isFocusable)
                        assertTrue(binding.resizeTextView.isFocusable)
                        assertTrue(binding.captionTextView.isFocusable)
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
            preservePreviews()
        } finally {
            prefs.edit().apply { previous?.let { putBoolean(key, it) } ?: remove(key) }.commit()
        }
    }

    @Test
    fun narrowFullscreenMetadataUsesFullWidthAndRestoresAfterResize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(false, true).forEach { rtl ->
                val themed = ContextThemeWrapper(context, R.style.DarkTheme)
                val binding = PlayerBinding.inflate(LayoutInflater.from(themed))
                binding.root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                binding.playbackControlRoot.visibility = View.VISIBLE
                binding.loadingPanel.visibility = View.GONE
                binding.metadataView.visibility = View.VISIBLE
                binding.titleTextView.text = "A long vertical video title that must remain identifiable"
                binding.channelTextView.text = "Example channel"
                binding.qualityTextView.text = "1080p"
                binding.playbackSpeed.text = "1×"
                val metadataParent = binding.metadataView.parent
                val controlsParent = binding.primaryControls.parent
                val metadataParams = binding.metadataView.layoutParams
                val controlsParams = binding.primaryControls.layoutParams
                val controller = NarrowPlayerMetadata(binding)
                measure(binding.root, dp(360), dp(800))
                repeat(3) {
                    controller.update(true)
                    measure(binding.root, dp(360), dp(800))
                    assertSame(binding.topControls, binding.metadataView.parent)
                    assertTrue(binding.primaryControls.parent is HorizontalScrollView)
                    assertTrue("Title must keep most of the portrait width", binding.titleTextView.width >= dp(240))
                    assertTrue("Channel must remain readable", binding.channelTextView.width >= dp(180))
                    val metadata = bounds(binding, binding.metadataView)
                    val controls = bounds(binding, binding.primaryControls)
                    val quality = bounds(binding, binding.qualityTextView)
                    assertTrue("Quality control must remain in the initial viewport", quality.left >= 0 && quality.right <= binding.root.width)
                    assertTrue("Metadata must not overlap action controls", metadata.bottom <= controls.top)
                    assertNotNull(binding.captionTextView.drawable)
                    assertTrue(binding.captionTextView.isFocusable)
                    assertTrue(binding.captionTextView.contentDescription.isNotBlank())
                    if (it == 0) savePreview(binding, "portrait-metadata${if (rtl) "-rtl" else ""}")
                    measure(binding.root, dp(800), dp(450))
                    controller.update(true)
                    assertSame(metadataParent, binding.metadataView.parent)
                    assertSame(controlsParent, binding.primaryControls.parent)
                    assertSame(metadataParams, binding.metadataView.layoutParams)
                    assertSame(controlsParams, binding.primaryControls.layoutParams)
                    measure(binding.root, dp(360), dp(800))
                }
                controller.update(true)
                controller.update(false)
                assertSame(metadataParent, binding.metadataView.parent)
                assertSame(controlsParent, binding.primaryControls.parent)
            }
        }
        preservePreviews()
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
        val file = File(folder, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun preservePreviews() {
        // Connected tests uninstall the app; preserve previews outside its data directory.
        // UiAutomation must connect off the main thread on older Android versions.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val folder = File(context.getExternalFilesDir(null), "player-layout-previews")
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        val commands = listOf("mkdir -p $destination") + folder.listFiles().orEmpty().map { "cp ${it.absolutePath} $destination/" }
        commands.forEach { command ->
            val output = automation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
