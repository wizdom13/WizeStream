package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenRefreshException;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRedirectException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;

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
    public void acceptsOnlyBoundedSabrRedirectFailures() {
        final Throwable redirectFailure = new RuntimeException("source",
                new IOException("SABR logic failure",
                        new SabrRedirectException("SABR redirect limit exceeded")));

        assertTrue(PlayerHttpErrorRecovery.hasSabrRedirectCause(redirectFailure));
        assertTrue(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(redirectFailure));

        final Throwable unrelatedProtocolFailure = new RuntimeException("source",
                new IOException("SABR logic failure",
                        new SabrProtocolException("SABR malformed media")));
        assertFalse(PlayerHttpErrorRecovery.hasSabrRedirectCause(unrelatedProtocolFailure));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(
                unrelatedProtocolFailure));
    }

    @Test
    public void acceptsOnlyExhaustedSabrPoTokenRefreshFailures() {
        final SabrPoTokenRefreshException refreshFailure =
                new SabrPoTokenRefreshException(
                        "video-id", "SABR protected response after token refreshes");
        final Throwable tokenFailure = new RuntimeException("source",
                new IOException("SABR logic failure", refreshFailure));

        assertTrue(PlayerHttpErrorRecovery.hasSabrPoTokenRefreshCause(tokenFailure));
        assertEquals(refreshFailure,
                PlayerHttpErrorRecovery.findSabrPoTokenRefreshCause(tokenFailure));
        assertEquals("video-id",
                PlayerHttpErrorRecovery.findSabrPoTokenRefreshCause(tokenFailure).getVideoId());
        assertTrue(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(tokenFailure));

        final Throwable unrelatedProtocolFailure = new RuntimeException("source",
                new IOException("SABR logic failure",
                        new SabrProtocolException("SABR malformed protection response")));
        assertFalse(PlayerHttpErrorRecovery.hasSabrPoTokenRefreshCause(
                unrelatedProtocolFailure));
        assertEquals(null,
                PlayerHttpErrorRecovery.findSabrPoTokenRefreshCause(unrelatedProtocolFailure));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(
                unrelatedProtocolFailure));
    }

    @Test
    public void retryGuardAllowsOneRetryPerCurrentKey() {
        final PlayerHttpErrorRecovery.RecoveryGuard guard =
                new PlayerHttpErrorRecovery.RecoveryGuard();

        assertTrue(guard.canRetry("youtube:https://example.com/watch?v=one"));
        assertFalse(guard.canRetry("youtube:https://example.com/watch?v=one"));
        assertTrue(guard.canRetry("youtube:https://example.com/watch?v=two"));
        assertFalse(guard.canRetry("youtube:https://example.com/watch?v=two"));
        assertTrue(guard.canRetry("youtube:https://example.com/watch?v=one"));
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
}
