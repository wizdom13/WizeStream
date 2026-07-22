package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

public class SleepTimerTest {
    private static final float DELTA = 0.001f;

    @Test
    public void durationUsesElapsedRealtimeAndClampsAtZero() {
        final AtomicLong clock = new AtomicLong(1_000L);
        final SleepTimer timer = new SleepTimer(clock::get);

        timer.startDuration(60_000L, false);
        assertEquals(60_000L, timer.getDurationRemainingMillis());
        assertFalse(timer.hasDurationExpired());

        clock.addAndGet(30_500L);
        assertEquals(29_500L, timer.getDurationRemainingMillis());

        clock.addAndGet(40_000L);
        assertEquals(0L, timer.getDurationRemainingMillis());
        assertTrue(timer.hasDurationExpired());
    }

    @Test
    public void endModesHaveNoWallClockDeadline() {
        final SleepTimer timer = new SleepTimer(() -> 10_000L);

        timer.startEndOfCurrent(true);
        assertEquals(SleepTimer.Mode.END_OF_CURRENT, timer.getMode());
        assertEquals(SleepTimer.REMAINING_TIME_UNSET, timer.getDurationRemainingMillis());
        assertFalse(timer.hasDurationExpired());

        timer.startEndOfQueue(false);
        assertEquals(SleepTimer.Mode.END_OF_QUEUE, timer.getMode());
        assertFalse(timer.isFadeOutEnabled());
    }

    @Test
    public void fadeOutIsLinearDuringFinalThirtySeconds() {
        final SleepTimer timer = new SleepTimer(() -> 0L);
        timer.startDuration(60_000L, true);

        assertEquals(1.0f, timer.getFadeOutVolumeMultiplier(40_000L), DELTA);
        assertEquals(1.0f, timer.getFadeOutVolumeMultiplier(30_000L), DELTA);
        assertEquals(1.0f, timer.getFadeOutVolumeMultiplier(29_000L), DELTA);
        assertEquals(0.5f, timer.getFadeOutVolumeMultiplier(14_500L), DELTA);
        assertEquals(0.0f, timer.getFadeOutVolumeMultiplier(0L), DELTA);
    }

    @Test
    public void cancelClearsModeAndRestoresFullVolume() {
        final SleepTimer timer = new SleepTimer(() -> 0L);
        timer.startDuration(10_000L, true);

        timer.cancel();

        assertFalse(timer.isActive());
        assertEquals(SleepTimer.Mode.NONE, timer.getMode());
        assertEquals(1.0f, timer.getFadeOutVolumeMultiplier(0L), DELTA);
    }
}
