package org.schabi.newpipe.player.datasource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getSafariUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isSafariStreamingUrl;

import org.junit.Test;
import org.schabi.newpipe.DownloaderImpl;

public class YoutubeHttpDataSourceTest {
    private static final String STREAM_URL =
            "https://rr1---sn.example.googlevideo.com/videoplayback?itag=18";

    @Test
    public void safariWebStreamUsesSafariUserAgent() {
        final String url = STREAM_URL + "&c=WEB&cver=2.20260114.08.00";

        assertTrue(isSafariStreamingUrl(url));
        assertEquals(getSafariUserAgent(), YoutubeHttpDataSource.resolveUserAgent(url));
    }

    @Test
    public void regularWebStreamUsesDefaultUserAgent() {
        final String url = STREAM_URL + "&c=WEB&cver=2.20241126.01.00";

        assertFalse(isSafariStreamingUrl(url));
        assertEquals(DownloaderImpl.USER_AGENT, YoutubeHttpDataSource.resolveUserAgent(url));
    }

    @Test
    public void safariVersionWithoutWebClientUsesDefaultUserAgent() {
        final String url = STREAM_URL + "&c=TVHTML5_SIMPLY_EMBEDDED_PLAYER"
                + "&cver=2.20260114.08.00";

        assertFalse(isSafariStreamingUrl(url));
        assertEquals(DownloaderImpl.USER_AGENT, YoutubeHttpDataSource.resolveUserAgent(url));
    }

    @Test
    public void androidStreamKeepsAndroidUserAgent() {
        final String url = STREAM_URL + "&c=ANDROID&cver=21.03.36";

        assertEquals(getAndroidUserAgent(null), YoutubeHttpDataSource.resolveUserAgent(url));
    }

    @Test
    public void iosStreamKeepsIosUserAgent() {
        final String url = STREAM_URL + "&c=IOS&cver=19.45.4";

        assertEquals(getIosUserAgent(null), YoutubeHttpDataSource.resolveUserAgent(url));
    }

    @Test
    public void visionOsStreamKeepsVisionOsUserAgent() {
        final String url = STREAM_URL + "&c=VISIONOS&cver=1.02";

        assertEquals(getVisionOsUserAgent(null), YoutubeHttpDataSource.resolveUserAgent(url));
    }

    @Test
    public void pathEncodedClientMarkersUseMatchingUserAgents() {
        final String pathStreamUrl =
                "https://rr1---sn.example.googlevideo.com/videoplayback/itag/18";

        assertEquals(getSafariUserAgent(), YoutubeHttpDataSource.resolveUserAgent(
                pathStreamUrl + "/c/WEB/cver/2.20260114.08.00"));
        assertEquals(getAndroidUserAgent(null), YoutubeHttpDataSource.resolveUserAgent(
                pathStreamUrl + "/c/ANDROID/cver/21.03.36"));
        assertEquals(getIosUserAgent(null), YoutubeHttpDataSource.resolveUserAgent(
                pathStreamUrl + "/c/IOS/cver/19.45.4"));
        assertEquals(getVisionOsUserAgent(null), YoutubeHttpDataSource.resolveUserAgent(
                pathStreamUrl + "/c/VISIONOS/cver/1.02"));
    }

    @Test
    public void rejectedRequestDiagnosticExcludesSignedUrlData() {
        final String url = STREAM_URL + "&c=VISIONOS&cver=1.02"
                + "&sig=secret-signature";

        final String diagnostic = YoutubeHttpDataSource.buildSafeRequestDiagnostic(url);

        assertEquals("client=VISIONOS, cver=1.02, itag=18, userAgent=VISIONOS",
                diagnostic);
        assertFalse(diagnostic.contains("secret-signature"));
        assertFalse(diagnostic.contains("googlevideo.com"));
    }

    @Test
    public void rejectedRequestDiagnosticReadsPathEncodedParameters() {
        final String url = "https://rr1---sn.example.googlevideo.com/videoplayback"
                + "/itag/18/c/VISIONOS/cver/1.02/sig/secret-signature";

        final String diagnostic = YoutubeHttpDataSource.buildSafeRequestDiagnostic(url);

        assertEquals("client=VISIONOS, cver=1.02, itag=18, userAgent=VISIONOS", diagnostic);
        assertFalse(diagnostic.contains("secret-signature"));
        assertFalse(diagnostic.contains("googlevideo.com"));
    }
}
