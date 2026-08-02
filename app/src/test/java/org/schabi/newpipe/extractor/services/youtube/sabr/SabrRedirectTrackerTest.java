package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabrRedirectTrackerTest {
    @Test
    void allowsEightDistinctRedirects() throws Exception {
        final SabrRedirectTracker tracker = new SabrRedirectTracker(
                "https://start.googlevideo.com/videoplayback", 8);

        for (int redirect = 1; redirect <= 8; redirect++) {
            tracker.follow("https://rr" + redirect
                    + ".googlevideo.com/videoplayback?id=video");
        }

        final SabrRedirectException error = assertThrows(SabrRedirectException.class,
                () -> tracker.follow("https://rr9.googlevideo.com/videoplayback?id=video"));
        assertTrue(error.getMessage().contains("limit exceeded: redirects=9"));
    }

    @Test
    void rejectsRedirectLoopsBeforeTheLimit() throws Exception {
        final SabrRedirectTracker tracker = new SabrRedirectTracker(
                "https://start.googlevideo.com/videoplayback", 8);
        tracker.follow("https://rr1.googlevideo.com/videoplayback?id=video");
        tracker.follow("https://rr2.googlevideo.com/videoplayback?id=video");

        final SabrRedirectException error = assertThrows(SabrRedirectException.class,
                () -> tracker.follow("https://RR1.GOOGLEVIDEO.COM/videoplayback?id=video#ignored"));
        assertTrue(error.getMessage().contains("loop detected: redirects=3"));
    }

    @Test
    void successfulMediaProgressStartsANewChain() throws Exception {
        final SabrRedirectTracker tracker = new SabrRedirectTracker(
                "https://start.googlevideo.com/videoplayback", 1);
        final String firstRedirect = "https://rr1.googlevideo.com/videoplayback?id=video";
        tracker.follow(firstRedirect);

        tracker.reset(firstRedirect);

        assertDoesNotThrow(() -> tracker.follow(
                "https://rr2.googlevideo.com/videoplayback?id=video"));
    }
}
