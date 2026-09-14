package org.schabi.newpipe.player.video

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Base64
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.ui.PlayerUi

class VideoAdjustmentLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun enablingDisablingAndFailureRecoveryPreserveTheRealPlayerQueueAndPauseState() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = listOf(R.string.playback_speed_key, R.string.playback_pitch_key, R.string.playback_skip_silence_key)
            .map(context::getString)
        val savedPreferences = keys.associateWith { preferences.all[it] }
        val video = File(context.cacheDir, "video-adjustment-lifecycle.mp4")
        val encoded = instrumentation.context.assets.open("video-adjustments/red-sdr.mp4.base64").use { it.readBytes() }
        video.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        val connected = CountDownLatch(1)
        lateinit var service: PlayerService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as PlayerService.LocalBinder).service
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        var bound = false
        var player: Player? = null
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity {
                    bound = context.bindService(
                        Intent(context, PlayerService::class.java).setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION),
                        connection,
                        Context.BIND_AUTO_CREATE
                    )
                }
                assertTrue(bound)
                assertTrue(connected.await(10, TimeUnit.SECONDS))
                val ready = AtomicReference(CountDownLatch(1))
                val queue = SinglePlayQueue(localItem(video, "First"))
                queue.append(listOf(localItem(video, "Second")))
                queue.index = 1
                queue.setRecovery(queue.index, 1500)
                scenario.onActivity { activity ->
                    val surface = SurfaceView(activity)
                    activity.setContentView(surface, FrameLayout.LayoutParams(320, 180))
                    player = Player(service, service.mediaSession, service.mediaSession.player).apply {
                        UIs().addAndPrepare(object : PlayerUi(this) {
                            override fun initPlayer() {
                                getPlayer().exoPlayer.setVideoSurfaceView(surface)
                            }

                            override fun destroyPlayer() {
                                getPlayer().getExoPlayer()?.clearVideoSurfaceView(surface)
                            }

                            override fun onPrepared() {
                                ready.get().countDown()
                            }
                        })
                        initPlayback(queue, false)
                    }
                }
                assertTrue(ready.get().await(20, TimeUnit.SECONDS))
                lateinit var originalOrder: List<PlayQueueItem>
                scenario.onActivity {
                    player!!.setPlaybackParameters(1.25f, 1.1f, true)
                    player!!.exoPlayer.repeatMode = Media3Player.REPEAT_MODE_ONE
                    player!!.exoPlayer.shuffleModeEnabled = true
                    player!!.startSleepTimer(120000, false)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { originalOrder = queue.streams.toList() }
                for (action in listOf("enable", "disable", "enable", "failure")) {
                    ready.set(CountDownLatch(1))
                    scenario.onActivity {
                        val active = player!!
                        val before = active.exoPlayer
                        if (action == "failure") {
                            active.onPlayerError(PlaybackException("Injected GPU failure", null, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED))
                        } else {
                            active.videoAdjustments.update(VideoAdjustmentState(enabled = action == "enable", saturation = 0))
                        }
                        assertNotSame(before, active.exoPlayer)
                    }
                    assertTrue("Playback did not recover after $action", ready.get().await(20, TimeUnit.SECONDS))
                    scenario.onActivity {
                        val active = player!!
                        assertSame(queue, active.playQueue)
                        assertEquals(originalOrder, queue.streams)
                        assertEquals("Second", queue.item!!.title)
                        assertEquals(1500L, active.exoPlayer.currentPosition)
                        assertFalse(active.exoPlayer.playWhenReady)
                        assertEquals(1.25f, active.exoPlayer.playbackParameters.speed)
                        assertEquals(1.1f, active.exoPlayer.playbackParameters.pitch)
                        assertTrue(active.exoPlayer.skipSilenceEnabled)
                        assertEquals(Media3Player.REPEAT_MODE_ONE, active.exoPlayer.repeatMode)
                        assertTrue(active.exoPlayer.shuffleModeEnabled)
                        assertTrue(active.isSleepTimerActive)
                        assertEquals(action == "enable", active.videoAdjustments.pipelineActive)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { player?.destroy() }
            if (bound) context.unbindService(connection)
            video.delete()
            preferences.edit().apply {
                savedPreferences.forEach { (key, value) ->
                    when (value) {
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        }
    }

    private fun localItem(video: File, title: String): PlayQueueItem = PlayQueueItem.localMedia(
        title, video.toURI().toString(), 4, null, null, null, "video/mp4", -1, true, null
    )
}
