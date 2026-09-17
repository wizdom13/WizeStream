package org.schabi.newpipe.player.mediasession

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.session.MediaController
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.notification.NotificationActionData
import org.schabi.newpipe.player.notification.NotificationConstants

class PlayerServiceNotificationTest {
    @Test
    fun localBindingRegistersTheSessionAndPublishesPlatformCustomActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
                scenario.onActivity { assertTrue(service.sessions.contains(service.mediaSession)) }
                var notificationConnected = false
                for (attempt in 0 until 100) {
                    instrumentation.runOnMainSync {
                        notificationConnected = service.mediaSession.mediaNotificationControllerInfo != null
                    }
                    if (notificationConnected) break
                    Thread.sleep(50)
                }
                assertTrue("Media3 notification controller did not connect", notificationConnected)
                val expected = listOf(NotificationConstants.ACTION_REPEAT, NotificationConstants.ACTION_CLOSE)
                scenario.onActivity {
                    val session = service.mediaSession
                    val buttons = expected.mapIndexed { index, action ->
                        MediaSessionActionProvider.buttonFor(
                            NotificationActionData(action, action, android.R.drawable.ic_media_play),
                            index == 0
                        )
                    }
                    session.setMediaButtonPreferences(session.mediaNotificationControllerInfo!!, buttons)
                    val platform = MediaController(context, session.platformToken)
                    assertEquals(expected, platform.playbackState.customActions.map { it.action })
                }
            }
        } finally {
            if (bound) context.unbindService(connection)
        }
    }
}
