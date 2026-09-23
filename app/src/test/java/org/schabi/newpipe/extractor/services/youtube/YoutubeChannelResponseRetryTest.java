package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeChannelResponseRetryTest {
    @Test
    public void retriesTransientServerErrorsWithinTheAttemptLimit() {
        assertTrue(YoutubeParsingHelper.shouldRetryChannelServerError(500, 1));
        assertTrue(YoutubeParsingHelper.shouldRetryChannelServerError(503, 2));
    }

    @Test
    public void stopsRetryingAfterTheAttemptLimit() {
        assertFalse(YoutubeParsingHelper.shouldRetryChannelServerError(500, 3));
    }

    @Test
    public void doesNotRetryPermanentClientErrors() {
        assertFalse(YoutubeParsingHelper.shouldRetryChannelServerError(404, 1));
        assertFalse(YoutubeParsingHelper.shouldRetryChannelServerError(403, 1));
    }
}
