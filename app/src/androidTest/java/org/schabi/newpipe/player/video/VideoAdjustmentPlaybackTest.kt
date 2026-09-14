package org.schabi.newpipe.player.video

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.player.helper.CustomRenderersFactory
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor

class VideoAdjustmentPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun decodesGrayscaleAndRebindsSurfacesWithBothVideoRenderers() {
        // Original four-second solid-red SDR fixture, generated with FFmpeg. Text encoding keeps
        // this small fixture portable; it is decoded into the test cache, never app downloads.
        val video = File(instrumentation.targetContext.cacheDir, "video-adjustment-test.mp4")
        val encoded = instrumentation.context.assets.open("video-adjustments/red-sdr.mp4.base64").use { it.readBytes() }
        video.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                for (workaround in listOf(false, true)) {
                    var engine: ExoPlayer? = null
                    lateinit var surface: SurfaceView
                    val rendered = AtomicReference(CountDownLatch(1))
                    val failure = AtomicReference<PlaybackException>()
                    val controller = VideoAdjustmentController(
                        VideoAdjustmentState(enabled = true, saturation = 0),
                        {},
                        {},
                        {}
                    )
                    try {
                        scenario.onActivity { activity ->
                            surface = SurfaceView(activity)
                            activity.setContentView(surface, FrameLayout.LayoutParams(320, 180))
                            engine = ExoPlayer.Builder(
                                activity,
                                CustomRenderersFactory(activity, workaround, VisualizerAudioProcessor())
                            ).build().apply {
                                addListener(object : Player.Listener {
                                    override fun onRenderedFirstFrame() {
                                        rendered.get().countDown()
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        failure.set(error)
                                        rendered.get().countDown()
                                    }
                                })
                                controller.attach(this)
                                setVideoSurfaceView(surface)
                                setMediaItem(MediaItem.fromUri(video.toURI().toString()))
                                seekTo(1000)
                                prepare()
                            }
                        }
                        awaitFrame(rendered.get(), failure)
                        assertGrayscale(surface)
                        rendered.set(CountDownLatch(1))
                        scenario.onActivity { activity ->
                            engine!!.clearVideoSurfaceView(surface)
                            surface = SurfaceView(activity)
                            activity.setContentView(surface, FrameLayout.LayoutParams(240, 135))
                            engine!!.setVideoSurfaceView(surface)
                            engine!!.seekTo(1500)
                            assertFalse(engine!!.playWhenReady)
                            assertEquals(0, controller.state.saturation)
                        }
                        awaitFrame(rendered.get(), failure)
                        assertGrayscale(surface)
                        scenario.onActivity {
                            assertEquals(1500L, engine!!.currentPosition)
                            assertTrue(controller.pipelineActive)
                        }
                    } finally {
                        instrumentation.runOnMainSync {
                            controller.detach()
                            engine?.release()
                        }
                    }
                }
            }
        } finally {
            video.delete()
        }
    }

    private fun awaitFrame(latch: CountDownLatch, failure: AtomicReference<PlaybackException>) {
        assertTrue("Timed out waiting for the effects renderer", latch.await(20, TimeUnit.SECONDS))
        assertNull("Video processing failed: ${failure.get()}", failure.get())
    }

    private fun assertGrayscale(surface: SurfaceView) {
        if (Build.VERSION.SDK_INT < 26) return // API 23 still verifies actual decoding/surface swaps.
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            var result = PixelCopy.ERROR_SOURCE_NO_DATA
            // Media3 can announce the first frame before SurfaceFlinger has received its buffer.
            // Retry only that transient condition; retain the actual grayscale pixel assertions.
            for (attempt in 0 until 20) {
                val copied = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    PixelCopy.request(surface, bitmap, {
                        result = it
                        copied.countDown()
                    }, Handler(Looper.getMainLooper()))
                }
                assertTrue(copied.await(5, TimeUnit.SECONDS))
                if (result != PixelCopy.ERROR_SOURCE_NO_DATA) break
                Thread.sleep(50)
            }
            assertEquals(PixelCopy.SUCCESS, result)
            val color = bitmap.getPixel(16, 16)
            assertTrue("Expected a visible gray frame", Color.red(color) in 15..240)
            assertTrue(kotlin.math.abs(Color.red(color) - Color.green(color)) < 5)
            assertTrue(kotlin.math.abs(Color.green(color) - Color.blue(color)) < 5)
        } finally {
            bitmap.recycle()
        }
    }
}
