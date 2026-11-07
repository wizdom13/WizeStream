package org.schabi.newpipe.player.widget

import android.app.Application
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.Player

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackWidgetTest {
    @Test
    fun controlsExistingPlayerAndIgnoresUnrecognizedActions() {
        val context = RuntimeEnvironment.getApplication()
        val player = mock(Player::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        PlaybackWidget.update(context, player)
        val receiver = PlaybackWidget()
        receiver.onReceive(context, Intent("org.schabi.newpipe.widget.PLAY"))
        verify(player).playPause()
        verify(player).exoPlayerIsNull()
        receiver.onReceive(context, Intent("unknown"))
        verifyNoMoreInteractions(player)
        PlaybackWidget.update(context, null)
        receiver.onReceive(context, Intent("org.schabi.newpipe.widget.PLAY"))
        verifyNoMoreInteractions(player)
    }
}
