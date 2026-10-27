package org.schabi.newpipe.player

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PendingPlaybackRecoveryTest {
    @Test
    fun startupCannotReplacePendingTimestampButNormalProgressCanBeSavedAfterConsumption() {
        val player = mock<Player>()
        val engine = mock<ExoPlayer>()
        val queue = SinglePlayQueue(PlayQueueItem.localMedia("Video", "content://video/1", 300, null, null, null, "video/mp4", 1, true, null))
        queue.setRecovery(0, 120_000L)
        Mockito.`when`(player.playQueue).thenReturn(queue)
        Mockito.`when`(player.exoPlayer).thenReturn(engine)
        Mockito.`when`(engine.currentTimeline).thenReturn(mock<Timeline>())
        Mockito.`when`(engine.currentPosition).thenReturn(0L)
        Mockito.`when`(engine.duration).thenReturn(300_000L)
        val controller = PlayerLifecycleController(
            player, RuntimeEnvironment.getApplication(), mock(), mock(), mock(), mock(), mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), CompositeDisposable()
        )
        controller.setRecovery()
        assertEquals(120_000L, queue.item!!.recoveryPosition)
        queue.setRecovery(0, 0L)
        Mockito.`when`(engine.currentPosition).thenReturn(45_000L)
        controller.setRecovery()
        assertEquals(0L, queue.item!!.recoveryPosition)
        // Once this item is active, repeated UI position saves must follow current playback.
        Mockito.`when`(player.currentItem).thenReturn(queue.item)
        controller.setRecovery()
        assertEquals(45_000L, queue.item!!.recoveryPosition)
        Mockito.`when`(engine.currentPosition).thenReturn(90_000L)
        controller.setRecovery()
        assertEquals(90_000L, queue.item!!.recoveryPosition)
        queue.unsetRecovery(0)
        Mockito.`when`(engine.currentTimeline).thenReturn(Timeline.EMPTY)
        controller.setRecovery()
        assertEquals(PlayQueueItem.RECOVERY_UNSET, queue.item!!.recoveryPosition)
    }

    private inline fun <reified T> mock(): T = Mockito.mock(T::class.java)
}
