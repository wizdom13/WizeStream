package org.schabi.newpipe.util

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteKeyPressTest {
    private val press = TvRemoteKeyPress()
    private val performed = mutableListOf<TvRemoteAction>()
    private var assigned: TvRemoteAction? = TvRemoteAction.PLAY_PAUSE

    private fun send(
        event: Int,
        repeat: Int = 0,
        canceled: Boolean = false,
        eligible: Boolean = true,
        device: Int = 1
    ) = press.dispatch(KeyEvent.KEYCODE_PROG_RED, device, event, repeat, canceled, eligible, { assigned }, performed::add)

    @Test
    fun syncShortcutRunsOnceAndDoesNotRequirePlayback() {
        assigned = TvRemoteAction.SYNC
        assertFalse(TvRemoteAction.SYNC.playback)
        assertEquals(TvRemoteAction.SYNC, TvRemoteAction.fromId("sync"))
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_DOWN, repeat = 1)
        send(KeyEvent.ACTION_UP)
        assertEquals(listOf(TvRemoteAction.SYNC), performed)
    }

    @Test
    fun assignedPressConsumesDownRepeatsAndUpButPerformsOnlyOnceOnRelease() {
        assertTrue(send(KeyEvent.ACTION_DOWN))
        assertTrue(send(KeyEvent.ACTION_DOWN, repeat = 1))
        assertTrue(send(KeyEvent.ACTION_DOWN, repeat = 2))
        assertTrue(performed.isEmpty())
        assertTrue(send(KeyEvent.ACTION_UP))
        assertEquals(listOf(TvRemoteAction.PLAY_PAUSE), performed)
        assertFalse(send(KeyEvent.ACTION_UP))
    }

    @Test
    fun unassignedOrUnavailableActionsLeaveDefaultHandlingIntact() {
        assigned = null
        assertFalse(send(KeyEvent.ACTION_DOWN))
        assertFalse(send(KeyEvent.ACTION_UP))
        assigned = TvRemoteAction.SEARCH
        assertFalse(send(KeyEvent.ACTION_DOWN, eligible = false))
        assertFalse(send(KeyEvent.ACTION_UP))
        assertFalse(send(KeyEvent.ACTION_DOWN, repeat = 3))
        assertTrue(performed.isEmpty())
    }

    @Test
    fun canceledPressOrEnteringAnEditorBeforeReleaseDoesNotPerformAnAction() {
        assertTrue(send(KeyEvent.ACTION_DOWN))
        assertTrue(send(KeyEvent.ACTION_UP, canceled = true))
        assertTrue(send(KeyEvent.ACTION_DOWN))
        assertTrue(send(KeyEvent.ACTION_UP, eligible = false))
        assertTrue(performed.isEmpty())
    }

    @Test
    fun changingAnAssignmentWhileHeldDoesNotRunEitherAction() {
        send(KeyEvent.ACTION_DOWN)
        assigned = TvRemoteAction.SEARCH
        assertTrue(send(KeyEvent.ACTION_UP))
        assertTrue(performed.isEmpty())
    }

    @Test
    fun focusLossClearsHeldKeysAndCannotTriggerOnAStaleRelease() {
        send(KeyEvent.ACTION_DOWN)
        press.clear()
        assertFalse(send(KeyEvent.ACTION_UP))
        assertFalse(send(KeyEvent.ACTION_DOWN, repeat = 1))
        assertTrue(performed.isEmpty())
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_UP)
        assertEquals(1, performed.size)
    }

    @Test
    fun releaseFromAnotherDeviceDoesNotCompleteTheRemotePress() {
        send(KeyEvent.ACTION_DOWN, device = 1)
        assertFalse(send(KeyEvent.ACTION_UP, device = 2))
        assertTrue(performed.isEmpty())
        assertTrue(send(KeyEvent.ACTION_UP, device = 1))
        assertEquals(1, performed.size)
    }

    @Test
    fun focusEditingVolumeAndSystemKeysCannotBeAssigned() {
        listOf(
            KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_APP_SWITCH
        ).forEach { assertFalse("Reserved key $it", TvRemoteKeys.isAssignable(it)) }
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_CHANNEL_UP
        ).forEach { assertTrue("Assignable key $it", TvRemoteKeys.isAssignable(it)) }
    }
}
