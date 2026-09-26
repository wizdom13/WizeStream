package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;

import org.junit.Test;

public class AudioTrackRecoveryTest {
    @Test
    public void onlyAudioTrackInitializationFailureUsesEngineRecovery() {
        assertTrue(PlayerHttpErrorRecovery.isRecoverableAudioTrackInitFailure(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableAudioTrackInitFailure(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableAudioTrackInitFailure(
                PlaybackException.ERROR_CODE_TIMEOUT));
    }

    @Test
    public void audioTrackRecoveryIsOneShotPerQueueItem() {
        final PlayerHttpErrorRecovery.OneShotRecoveryGuard guard =
                new PlayerHttpErrorRecovery.OneShotRecoveryGuard();

        assertTrue(guard.acquire("youtube:https://example.com/watch?v=one"));
        assertFalse(guard.acquire("youtube:https://example.com/watch?v=one"));
        assertTrue(guard.acquire("youtube:https://example.com/watch?v=two"));
        assertFalse(guard.acquire("youtube:https://example.com/watch?v=two"));

        guard.reset();
        assertTrue(guard.acquire("youtube:https://example.com/watch?v=two"));
    }
}
