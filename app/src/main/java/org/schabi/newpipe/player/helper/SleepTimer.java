package org.schabi.newpipe.player.helper;

import android.os.SystemClock;

import androidx.annotation.NonNull;

/**
 * Stores sleep timer state independently from player views and playback lifecycle changes.
 */
public final class SleepTimer {
    public static final long FADE_OUT_DURATION_MILLIS = 30_000L;
    public static final long REMAINING_TIME_UNSET = -1L;

    /** Defines how the timer decides when playback should stop. */
    public enum Mode {
        NONE,
        DURATION,
        END_OF_CURRENT,
        END_OF_QUEUE
    }

    interface Clock {
        long elapsedRealtime();
    }

    @NonNull
    private final Clock clock;

    @NonNull
    private Mode mode = Mode.NONE;
    private long deadlineMillis;
    private long activeFadeWindowMillis;
    private boolean fadeOutEnabled;

    public SleepTimer() {
        this(SystemClock::elapsedRealtime);
    }

    SleepTimer(@NonNull final Clock clock) {
        this.clock = clock;
    }

    public void startDuration(final long durationMillis, final boolean fadeOut) {
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("Sleep timer duration must be positive");
        }

        mode = Mode.DURATION;
        final long now = clock.elapsedRealtime();
        deadlineMillis = durationMillis > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + durationMillis;
        activeFadeWindowMillis = 0L;
        fadeOutEnabled = fadeOut;
    }

    public void startEndOfCurrent(final boolean fadeOut) {
        startEndMode(Mode.END_OF_CURRENT, fadeOut);
    }

    public void startEndOfQueue(final boolean fadeOut) {
        startEndMode(Mode.END_OF_QUEUE, fadeOut);
    }

    private void startEndMode(@NonNull final Mode endMode, final boolean fadeOut) {
        mode = endMode;
        deadlineMillis = 0L;
        activeFadeWindowMillis = 0L;
        fadeOutEnabled = fadeOut;
    }

    public void cancel() {
        mode = Mode.NONE;
        deadlineMillis = 0L;
        activeFadeWindowMillis = 0L;
        fadeOutEnabled = false;
    }

    public boolean isActive() {
        return mode != Mode.NONE;
    }

    public boolean hasDurationExpired() {
        return mode == Mode.DURATION && getDurationRemainingMillis() == 0L;
    }

    public long getDurationRemainingMillis() {
        if (mode != Mode.DURATION) {
            return REMAINING_TIME_UNSET;
        }
        return Math.max(0L, deadlineMillis - clock.elapsedRealtime());
    }

    /**
     * Returns the volume multiplier for the final fade-out window.
     *
     * @param remainingMillis wall-clock time remaining before the timer should stop playback
     * @return a value from 0 (silent) to 1 (full volume)
     */
    public float getFadeOutVolumeMultiplier(final long remainingMillis) {
        if (!isActive() || !fadeOutEnabled || remainingMillis == REMAINING_TIME_UNSET) {
            return 1.0f;
        }
        if (remainingMillis <= 0L) {
            return 0.0f;
        }
        if (remainingMillis >= FADE_OUT_DURATION_MILLIS) {
            activeFadeWindowMillis = 0L;
            return 1.0f;
        }
        if (activeFadeWindowMillis == 0L) {
            // Starting a timer inside the final 30 seconds should not cause an abrupt volume jump.
            activeFadeWindowMillis = remainingMillis;
        }
        return Math.min(1.0f, (float) remainingMillis / activeFadeWindowMillis);
    }

    @NonNull
    public Mode getMode() {
        return mode;
    }

    public boolean isFadeOutEnabled() {
        return fadeOutEnabled;
    }
}
