package org.schabi.newpipe.extractor.services.bitchute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BitchuteParserHelperTest {
    @Test
    public void extractsSearchAuthorizationTokensFromExpectedMarkup() {
        assertTrue(BitchuteParserHelper.extractAndStoreSearchAuth(
                "<script>searchAuth('2026-09-06T07:00:00.000000+00:00', 'nonce-123')</script>"));
    }

    @Test
    public void rejectsSearchAuthorizationMarkupWhenEitherTokenIsMissing() {
        assertFalse(BitchuteParserHelper.extractAndStoreSearchAuth(
                "<script>searchAuth('2026-09-06T07:00:00.000000+00:00')</script>"));
        assertFalse(BitchuteParserHelper.extractAndStoreSearchAuth(
                "<script>const searchAuth = 'not-a-call';</script>"));
    }

    @Test
    public void extractsAndCachesCommentAuthorizationPerVideo() {
        final String firstId = "test-video-a";
        final String secondId = "test-video-b";

        assertTrue(BitchuteParserHelper.extractAndStoreCfAuth(
                firstId, "window.config = {cf_auth: 'auth-a', other: true};"));
        assertTrue(BitchuteParserHelper.extractAndStoreCfAuth(
                secondId, "window.config = {cf_auth: 'auth-b'};"));

        assertEquals("auth-a", BitchuteParserHelper.getCfAuth(firstId));
        assertEquals("auth-b", BitchuteParserHelper.getCfAuth(secondId));
    }

    @Test
    public void malformedCommentAuthorizationDoesNotCreateCacheEntry() {
        final String id = "test-video-missing-auth";

        assertFalse(BitchuteParserHelper.extractAndStoreCfAuth(
                id, "window.config = {comments_enabled: true};"));
        assertNull(BitchuteParserHelper.getCfAuth(id));
    }

    @Test
    public void prependsCanonicalBitChuteBaseUrl() {
        assertEquals("https://www.bitchute.com/video/example/",
                BitchuteParserHelper.prependBaseUrl("/video/example/"));
    }
}
