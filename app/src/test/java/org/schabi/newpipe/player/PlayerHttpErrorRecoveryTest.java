package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.datasource.HttpDataSource;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

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
    public void buildsSafeErrorContextFromNestedRequestDiagnostic() {
        final IOException diagnostic = new IOException("YouTube media request diagnostic: "
                + "client=VISIONOS, cver=1.02, itag=18, userAgent=VISIONOS");
        final Throwable error = new RuntimeException("source",
                invalidResponseCodeException(403, diagnostic));

        assertEquals("status=403, client=VISIONOS, cver=1.02, itag=18, userAgent=VISIONOS",
                PlayerHttpErrorRecovery.buildSafeErrorContext(error));
    }

    @Test
    public void avoidsOnlyRejectedAndroidVrAv1HfrStreams() throws Exception {
        final VideoStream av1Hfr = new VideoStream.Builder()
                .setId("398")
                .setContent("https://media.example.com/video", true)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setResolution("720p HFR")
                .setCodec("av01.0.08M.08")
                .setFps(60)
                .setIsVideoOnly(true)
                .setItagItem(ItagItem.getItag(398))
                .build();
        final IOException androidVrDiagnostic = new IOException(
                "YouTube media request diagnostic: client=ANDROID_VR, itag=398");
        final Throwable rejected = invalidResponseCodeException(403, androidVrDiagnostic);

        assertTrue(PlayerHttpErrorRecovery.shouldAvoidAndroidVrAv1HfrStream(rejected, av1Hfr));
        assertFalse(PlayerHttpErrorRecovery.shouldAvoidAndroidVrAv1HfrStream(
                invalidResponseCodeException(404, androidVrDiagnostic), av1Hfr));
        assertFalse(PlayerHttpErrorRecovery.shouldAvoidAndroidVrAv1HfrStream(
                invalidResponseCodeException(403, new IOException(
                        "YouTube media request diagnostic: client=VISIONOS, itag=398")), av1Hfr));
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
        assertTrue(recoveryMethod.contains("invalidateYouTubeMediaCaches(item)"));
        assertTrue(recoveryMethod.contains("rejectVideoStreamOnce"));
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
        return invalidResponseCodeException(responseCode, new IOException("HTTP error"));
    }

    private static HttpDataSource.InvalidResponseCodeException invalidResponseCodeException(
            final int responseCode,
            final IOException cause) {
        return new HttpDataSource.InvalidResponseCodeException(responseCode, "HTTP error",
                cause, Collections.emptyMap(), null, new byte[0]);
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
