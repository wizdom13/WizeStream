package org.schabi.newpipe.player

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ABRepeatTest {
    @Test
    fun loopsOnlyWhilePlayingAndStopsAfterClear() {
        val engine = mock(ExoPlayer::class.java)
        `when`(engine.isCurrentMediaItemSeekable).thenReturn(true)
        `when`(engine.duration).thenReturn(10000)
        `when`(engine.currentPosition).thenReturn(1000)
        val preferences = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        preferences.edit().putBoolean(OptionalPlaybackFeatures.AB_REPEAT_KEY, true).commit()
        val repeat = ABRepeat(preferences)
        repeat.attach(engine)
        assertFalse(repeat.markEnd())
        assertTrue(repeat.markStart())
        `when`(engine.currentPosition).thenReturn(2000)
        assertTrue(repeat.markEnd())
        verify(engine).seekTo(1000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        verify(engine, times(1)).seekTo(1000)
        `when`(engine.isPlaying).thenReturn(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        verify(engine, times(2)).seekTo(1000)
        repeat.clear()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        verify(engine, times(2)).seekTo(1000)
        repeat.detach()
        verify(engine).removeListener(repeat)
    }

    @Test
    fun rejectsLiveAndInvalidRangesAndClearsOnTransition() {
        val engine = mock(ExoPlayer::class.java)
        `when`(engine.isCurrentMediaItemSeekable).thenReturn(true)
        `when`(engine.duration).thenReturn(10000)
        val preferences = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        preferences.edit().putBoolean(OptionalPlaybackFeatures.AB_REPEAT_KEY, true).commit()
        val repeat = ABRepeat(preferences)
        repeat.attach(engine)
        `when`(engine.isCurrentMediaItemLive).thenReturn(true)
        assertFalse(repeat.markStart())
        `when`(engine.isCurrentMediaItemLive).thenReturn(false)
        assertTrue(repeat.markStart())
        assertFalse(repeat.markEnd())
        repeat.onMediaItemTransition(null, 0)
        assertNull(repeat.startMs)
        assertFalse(ABRepeat.validRange(1000, 999))
        assertFalse(ABRepeat.validRange(1000, 1499))
        assertTrue(ABRepeat.validRange(1000, 1500))
        repeat.detach()
    }

    @Test
    fun disabledByDefaultAndDisablingAnActiveRangeCancelsLooping() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        preferences.edit().remove(OptionalPlaybackFeatures.AB_REPEAT_KEY).commit()
        val repeat = ABRepeat(preferences)
        val engine = mock(ExoPlayer::class.java)
        `when`(engine.isCurrentMediaItemSeekable).thenReturn(true)
        `when`(engine.duration).thenReturn(10_000)
        `when`(engine.currentPosition).thenReturn(1_000)
        repeat.attach(engine)
        assertFalse(repeat.available())
        assertFalse(repeat.markStart())
        preferences.edit().putBoolean(OptionalPlaybackFeatures.AB_REPEAT_KEY, true).commit()
        assertTrue(repeat.markStart())
        `when`(engine.currentPosition).thenReturn(2_000)
        assertTrue(repeat.markEnd())
        assertTrue(repeat.active)
        verify(engine).seekTo(1_000)

        preferences.edit().putBoolean(OptionalPlaybackFeatures.AB_REPEAT_KEY, false).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(repeat.active)
        assertNull(repeat.startMs)
        assertNull(repeat.endMs)
        assertFalse(repeat.markEnd())
        `when`(engine.isPlaying).thenReturn(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        verify(engine, times(1)).seekTo(1_000)
        preferences.edit().putBoolean(OptionalPlaybackFeatures.AB_REPEAT_KEY, true).commit()
        assertTrue(repeat.available())
        assertFalse(repeat.active)
        repeat.detach()
    }
}
