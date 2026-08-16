package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerHttpErrorRecoveryTest {
    @Test
    public void findsInvalidResponseCodeInsideCauseChain() {
        final Throwable error = new RuntimeException("source", new IOException("network",
                invalidResponseCodeException(404)));

        assertEquals(Integer.valueOf(404), PlayerHttpErrorRecovery.findInvalidResponseCode(error));
    }

    @Test
    public void acceptsOnlyRecoverableStatusCodes() {
        assertTrue(PlayerHttpErrorRecovery.isRecoverableStatusCode(403));
        assertTrue(PlayerHttpErrorRecovery.isRecoverableStatusCode(404));
        assertTrue(PlayerHttpErrorRecovery.isRecoverableStatusCode(410));

        assertFalse(PlayerHttpErrorRecovery.isRecoverableStatusCode(400));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableStatusCode(500));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableStatusCode(null));
    }

    @Test
    public void findsUnknownHostExceptionInsideCauseChain() {
        final Throwable error = new RuntimeException("source", new IOException("network",
                new UnknownHostException("media.example.com")));

        assertTrue(PlayerHttpErrorRecovery.hasUnknownHostCause(error));
        assertTrue(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(error));
        assertFalse(PlayerHttpErrorRecovery.hasUnknownHostCause(
                new RuntimeException("source", new IOException("network"))));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(
                new RuntimeException("source", new IOException("network"))));
    }

    @Test
    public void retryGuardBoundsAttemptsAndAppliesBackoff() {
        final AtomicLong elapsedRealtime = new AtomicLong(1);
        final PlayerHttpErrorRecovery.RecoveryGuard guard =
                new PlayerHttpErrorRecovery.RecoveryGuard(elapsedRealtime::get);

        assertAttempt(guard.acquireAttempt("youtube:https://example.com/watch?v=one"), 1, 0);
        assertAttempt(guard.acquireAttempt("youtube:https://example.com/watch?v=one"),
                2, 1_000);
        assertAttempt(guard.acquireAttempt("youtube:https://example.com/watch?v=one"),
                3, 3_000);
        assertNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
    }

    @Test
    public void retryGuardResetsWhenCurrentItemChanges() {
        final PlayerHttpErrorRecovery.RecoveryGuard guard =
                new PlayerHttpErrorRecovery.RecoveryGuard(() -> 1);

        assertNotNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
        assertNotNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
        assertNotNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
        assertNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));

        assertAttempt(guard.acquireAttempt("youtube:https://example.com/watch?v=two"), 1, 0);
    }

    @Test
    public void retryGuardResetsAfterQuietWindow() {
        final AtomicLong elapsedRealtime = new AtomicLong(1);
        final PlayerHttpErrorRecovery.RecoveryGuard guard =
                new PlayerHttpErrorRecovery.RecoveryGuard(elapsedRealtime::get);

        assertNotNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
        assertNotNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
        assertNotNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));
        assertNull(guard.acquireAttempt("youtube:https://example.com/watch?v=one"));

        elapsedRealtime.addAndGet(PlayerHttpErrorRecovery.RecoveryGuard.RESET_AFTER_MILLIS);
        assertAttempt(guard.acquireAttempt("youtube:https://example.com/watch?v=one"), 1, 0);
    }

    @Test
    public void playerRecoveryPreservesPositionAndDoesNotSkipCurrentItem() throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/Player.java"));
        final int methodStart = source.indexOf(
                "private boolean tryRecoverFromYouTubeMediaUrlFailure");
        final int methodEnd = source.indexOf(
                "private void cancelPendingMediaUrlRecovery", methodStart);
        assertTrue(methodStart >= 0);
        assertTrue(methodEnd > methodStart);
        final String recoveryMethod = source.substring(methodStart, methodEnd);

        assertTrue(recoveryMethod.contains("setRecovery();"));
        assertTrue(recoveryMethod.indexOf("setRecovery();")
                < recoveryMethod.indexOf("postDelayed"));
        assertTrue(recoveryMethod.contains("InfoCache.getInstance()"));
        assertTrue(recoveryMethod.contains("changeState(STATE_PAUSED);"));
        assertFalse(recoveryMethod.contains("playQueue.error()"));
    }

    @Test
    public void acceptsOnlyYouTubeService() {
        assertTrue(PlayerHttpErrorRecovery.isYouTubeService(ServiceList.YouTube.getServiceId()));
        assertFalse(PlayerHttpErrorRecovery.isYouTubeService(
                ServiceList.SoundCloud.getServiceId()));
    }

    private static HttpDataSource.InvalidResponseCodeException invalidResponseCodeException(
            final int responseCode) {
        return new HttpDataSource.InvalidResponseCodeException(responseCode, "HTTP error",
                new IOException("HTTP error"), Collections.emptyMap(), null, new byte[0]);
    }

    private static void assertAttempt(
            final PlayerHttpErrorRecovery.RecoveryAttempt attempt,
            final int expectedNumber,
            final long expectedDelayMillis) {
        assertNotNull(attempt);
        assertEquals(expectedNumber, attempt.getNumber());
        assertEquals(expectedDelayMillis, attempt.getDelayMillis());
    }
}
