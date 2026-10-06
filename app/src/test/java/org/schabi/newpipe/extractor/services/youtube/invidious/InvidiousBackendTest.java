package org.schabi.newpipe.extractor.services.youtube.invidious;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class InvidiousBackendTest {
    @After
    public void reset() {
        InvidiousBackend.configure(false, "");
    }

    @Test
    public void requiresAnHttpsInstanceRoot() {
        assertEquals("https://example.org", InvidiousBackend
                .normalizeInstance(" https://EXAMPLE.org/ "));
        assertEquals("https://example.org:8443", InvidiousBackend
                .normalizeInstance("https://example.org:8443"));
        for (final String invalid : new String[]{"", "http://example.org",
                "https://example.org/api",
                "https://user:password@example.org", "https://example.org?local=true",
                        "https://example.org#x",
                "https://www.youtube.com", "https://example.org:0", "https://example.org:70000"}) {
            assertThrows(invalid, IllegalArgumentException.class,
                    () -> InvidiousBackend.normalizeInstance(invalid));
        }
    }

    @Test
    public void blocksDirectYoutubeInfrastructureAndRedirectsWithoutFallback() throws Exception {
        InvidiousBackend.configure(true, "https://example.org");
        for (final String host : new String[]{"youtube.com", "m.youtube.com", "youtu.be",
                "youtube-nocookie.com", "rr1.googlevideo.com", "i.ytimg.com", "yt3.ggpht.com",
                "yt3.googleusercontent.com", "youtubei.googleapis.com", "WWW.YOUTUBE.COM."}) {
            assertThrows(host, IOException.class,
                    () -> InvidiousBackend.checkRequest(URI.create("https://" + host + "/")));
        }
        InvidiousBackend.checkRequest(URI.create("https://notyoutube.com/"));
        InvidiousBackend.checkRequest(URI.create("https://youtube.com.example.org/"));
        InvidiousBackend.checkRedirect(URI.create("https://example.org/videoplayback"),
                URI.create("https://example.org:443/videoplayback?token=next"));
        for (final String target : new String[]{"https://rr1.googlevideo.com/videoplayback",
                "https://other.example.org/videoplayback", "http://example.org/videoplayback",
                "https://example.org:8443/videoplayback"}) {
            assertThrows(IOException.class, () -> InvidiousBackend.checkRedirect(
                    URI.create("https://example.org/videoplayback"), URI.create(target)));
        }
        InvidiousBackend.configure(false, "");
        InvidiousBackend.checkRequest(URI.create("https://www.youtube.com/watch?v=x"));
    }

    @Test
    public void restoredInvalidSettingsKeepBackendEnabledAndFailClosed() {
        InvidiousBackend.configure(true, "invalid");
        assertTrue(InvidiousBackend.isEnabled());
        assertThrows(IllegalArgumentException.class, InvidiousBackend::getInstance);
        assertThrows(IOException.class,
                () -> InvidiousBackend.checkRequest(URI.create("https://www.youtube.com/")));
    }

    @Test
    public void routesStoredThumbnailAndAvatarUrlsThroughTheInstance() {
        assertEquals("https://example.org/vi/abc/hqdefault.jpg", InvidiousBackend.routeImage(
                "https://i.ytimg.com/vi/abc/hqdefault.jpg", "https://example.org"));
        assertEquals("https://example.org/ggpht/avatar=s88?x=y", InvidiousBackend.routeImage(
                "//yt3.ggpht.com/avatar=s88?x=y", "https://example.org"));
        assertEquals("https://example.org/ggpht/avatar", InvidiousBackend.routeImage(
                "https://yt3.googleusercontent.com/avatar", "https://example.org"));
    }
}
