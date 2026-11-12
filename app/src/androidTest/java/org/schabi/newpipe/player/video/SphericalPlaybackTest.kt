package org.schabi.newpipe.player.video

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.ui.MinimizedVideoSurface

class SphericalPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun rendersPansAndKeepsTheSameSphereAcrossMiniatureAndLifecycleTransitions() {
        // Original four-second equirectangular fixture: four differently colored longitude bands.
        // Generated with FFmpeg, without audio or remote/network dependencies.
        val video = File(instrumentation.targetContext.cacheDir, "spherical-playback-test.mp4")
        video.writeBytes(Base64.decode(instrumentation.context.assets.open("spherical/longitude.mp4.base64").use { it.readBytes() }, Base64.DEFAULT))
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                var engine: ExoPlayer? = null
                var controller: PlayerVideoSurface? = null
                lateinit var binding: PlayerBinding
                lateinit var miniHost: FrameLayout
                lateinit var sphere: SurfaceView
                val state = SphericalPlaybackState()
                val rendered = AtomicReference(CountDownLatch(1))
                val failure = AtomicReference<PlaybackException>()
                val miniature = MinimizedVideoSurface()
                try {
                    scenario.onActivity { activity ->
                        binding = PlayerBinding.inflate(LayoutInflater.from(activity))
                        val root = FrameLayout(activity)
                        root.addView(binding.root, FrameLayout.LayoutParams(320, 180))
                        miniHost = FrameLayout(activity)
                        root.addView(miniHost, FrameLayout.LayoutParams(120, 68))
                        activity.setContentView(root)
                        engine = ExoPlayer.Builder(activity).build().apply {
                            addListener(object : Player.Listener {
                                override fun onRenderedFirstFrame() {
                                    rendered.get().countDown()
                                }
                                override fun onPlayerError(error: PlaybackException) {
                                    failure.set(error)
                                    rendered.get().countDown()
                                }
                            })
                            repeatMode = Player.REPEAT_MODE_ALL
                        }
                        controller = PlayerVideoSurface(binding, state, { engine }, { true }, { false }, { "fixture" }, {})
                        controller!!.setup()
                        controller!!.setMode(SphericalPlaybackState.Mode.EQUIRECTANGULAR)
                        sphere = controller!!.activeVideoView()
                        engine!!.setMediaItem(MediaItem.fromUri(video.toURI().toString()))
                        engine!!.seekTo(1_000)
                        engine!!.prepare()
                    }
                    awaitFrame(rendered.get(), failure)
                    val initialColor = awaitColor(sphere)
                    scenario.onActivity {
                        assertTrue(controller!!.isSpherical())
                        assertEquals(View.GONE, binding.surfaceView.visibility)
                        assertEquals(1_000L, engine!!.currentPosition)
                        assertFalse(engine!!.playWhenReady)
                        val time = SystemClock.uptimeMillis()
                        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                            val event = MotionEvent.obtain(time, time + index * 20L, action, if (index == 0) 100f else 2350f, 80f, 0)
                            assertTrue(controller!!.handleTouch(binding.root, event))
                            event.recycle()
                        }
                    }
                    if (initialColor != null) {
                        assertTrue("Dragging should change the sampled longitude", awaitColor(sphere, initialColor) != null)
                    }
                    rendered.set(CountDownLatch(1))
                    scenario.onActivity {
                        assertTrue(miniature.show(binding.videoSurfaceContainer, miniHost))
                        controller!!.restore()
                        assertTrue(binding.videoSurfaceContainer.miniPlayerMode)
                        assertSame(sphere, controller!!.activeVideoView())
                    }
                    awaitFrame(rendered.get(), failure)
                    rendered.set(CountDownLatch(1))
                    scenario.onActivity {
                        assertTrue(miniature.restore())
                        controller!!.restore()
                        assertSame(sphere, controller!!.activeVideoView())
                        assertFalse(binding.videoSurfaceContainer.miniPlayerMode)
                    }
                    awaitFrame(rendered.get(), failure)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity {
                        controller!!.restore()
                        assertSame(sphere, controller!!.activeVideoView())
                        assertEquals(1_000L, engine!!.currentPosition)
                    }
                    // Leaving projection must restore the ordinary surface without restarting playback.
                    rendered.set(CountDownLatch(1))
                    scenario.onActivity { binding.projectionButton.performClick() }
                    onView(withText(R.string.player_projection_flat)).perform(click())
                    awaitFrame(rendered.get(), failure)
                    scenario.onActivity {
                        assertFalse(controller!!.isSpherical())
                        assertSame(binding.surfaceView, controller!!.activeVideoView())
                        assertEquals(1_000L, engine!!.currentPosition)
                        controller!!.selectItem("tagged", true)
                        assertTrue(controller!!.isSpherical())
                        controller!!.selectItem("ordinary-next-video", false)
                        assertFalse(controller!!.isSpherical())
                        assertEquals(SphericalPlaybackState.Mode.AUTO, state.mode)
                    }
                    assertNull(failure.get())
                } finally {
                    instrumentation.runOnMainSync {
                        miniature.restore()
                        controller?.close()
                        engine?.release()
                    }
                }
            }
        } finally {
            video.delete()
        }
    }

    private fun awaitFrame(rendered: CountDownLatch, failure: AtomicReference<PlaybackException>) {
        assertTrue("Timed out waiting for the video output", rendered.await(20, TimeUnit.SECONDS))
        assertNull("Playback failed: ${failure.get()}", failure.get())
    }

    private fun awaitColor(surface: SurfaceView, previous: Int? = null): Int? {
        if (Build.VERSION.SDK_INT < 26) return null // API 23 exercises decoding and lifecycle changes.
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            repeat(30) {
                val copied = CountDownLatch(1)
                var result = PixelCopy.ERROR_SOURCE_NO_DATA
                instrumentation.runOnMainSync {
                    PixelCopy.request(surface, bitmap, {
                        result = it
                        copied.countDown()
                    }, Handler(Looper.getMainLooper()))
                }
                assertTrue(copied.await(5, TimeUnit.SECONDS))
                if (result == PixelCopy.SUCCESS) {
                    val color = bitmap.getPixel(8, 8)
                    val visible = maxOf(Color.red(color), Color.green(color), Color.blue(color)) > 80
                    val changed = previous == null || abs(Color.red(color) - Color.red(previous)) +
                        abs(Color.green(color) - Color.green(previous)) + abs(Color.blue(color) - Color.blue(previous)) > 100
                    if (visible && changed) return color
                }
                Thread.sleep(50)
            }
            throw AssertionError("No visible spherical frame or camera movement")
        } finally {
            bitmap.recycle()
        }
    }
}
