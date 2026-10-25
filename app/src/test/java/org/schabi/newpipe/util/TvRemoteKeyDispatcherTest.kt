package org.schabi.newpipe.util

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUiList

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class TvRemoteKeyDispatcherTest {
    @Test
    fun lockInterceptsBothHalvesBeforeShortcutsEvenWhenPlaybackIsBlocked() {
        val fixture = Fixture()
        fixture.keys.assign(TvRemoteAction.PLAY_PAUSE, KeyEvent.KEYCODE_PROG_RED)
        `when`(fixture.ui.isTouchLocked).thenReturn(true)
        `when`(fixture.player.currentState).thenReturn(Player.STATE_BLOCKED)

        for (code in listOf(KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER)) {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(action, code)
                `when`(fixture.ui.dispatchTouchLockedKeyEvent(event)).thenReturn(true)
                assertTrue(fixture.dispatcher.dispatch(event))
                verify(fixture.ui).dispatchTouchLockedKeyEvent(event)
            }
        }
        verify(fixture.player, never()).playPause()
        verify(fixture.player, never()).fastForward()
    }

    @Test
    fun lockingCancelsHeldShortcutAndUnlockingRestoresShortcuts() {
        val fixture = Fixture()
        fixture.keys.assign(TvRemoteAction.PLAY_PAUSE, KeyEvent.KEYCODE_PROG_RED)
        assertTrue(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED)))

        `when`(fixture.ui.isTouchLocked).thenReturn(true)
        // A system key still clears the pending shortcut without being consumed.
        assertFalse(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
        `when`(fixture.ui.isTouchLocked).thenReturn(false)
        assertFalse(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED)))
        verify(fixture.player, never()).playPause()

        assertTrue(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED)))
        assertTrue(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED)))
        verify(fixture.player).playPause()
    }

    @Test
    fun anotherActivityIsNotTrappedByThePlayerLock() {
        val fixture = Fixture()
        fixture.keys.assign(TvRemoteAction.PLAY_PAUSE, KeyEvent.KEYCODE_PROG_RED)
        `when`(fixture.ui.isTouchLocked).thenReturn(true)
        `when`(fixture.ui.parentActivity).thenReturn(Optional.of(mock(AppCompatActivity::class.java)))

        assertTrue(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED)))
        assertTrue(fixture.dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED)))
        verify(fixture.player).playPause()
    }

    private class Fixture {
        val activity = mock(AppCompatActivity::class.java)
        val player = mock(Player::class.java)
        val ui = mock(MainPlayerUi::class.java)
        val keys: TvRemoteKeys
        val dispatcher: TvRemoteKeyDispatcher

        init {
            val context = RuntimeEnvironment.getApplication()
            `when`(activity.packageName).thenReturn(context.packageName)
            `when`(activity.getSharedPreferences(context.packageName + "_preferences", 0))
                .thenReturn(context.getSharedPreferences("remote-dispatcher-test", 0))
            `when`(activity.hasWindowFocus()).thenReturn(true)
            keys = TvRemoteKeys(PreferenceManager.getDefaultSharedPreferences(activity))
            keys.reset()
            `when`(player.UIs()).thenReturn(PlayerUiList(ui))
            `when`(player.playQueue).thenReturn(mock(PlayQueue::class.java))
            `when`(player.currentState).thenReturn(Player.STATE_PLAYING)
            `when`(ui.parentActivity).thenReturn(Optional.of(activity))
            dispatcher = TvRemoteKeyDispatcher(activity) { player }
        }
    }
}
