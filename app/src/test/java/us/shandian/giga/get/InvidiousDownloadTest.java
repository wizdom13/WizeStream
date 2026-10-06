package us.shandian.giga.get;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.network.AppProxySelector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class InvidiousDownloadTest {
    @Before
    public void enable() {
        InvidiousBackend.configure(true, "https://example.org");
    }
    @After
    public void disable() {
        InvidiousBackend.configure(false, "");
    }

    private DownloadMission mission() {
        final DownloadMission mission = new DownloadMission(
                new String[]{"https://example.org/videoplayback"}, null, 'v', null);
        mission.source = "https://www.youtube.com/watch?v=BaW_jenozKc";
        return mission;
    }

    @Test
    public void rejectsStaleDirectDownloadUrls() throws Exception {
        assertThrows(IOException.class, () -> mission().openConnection(
                "https://rr1.googlevideo.com/videoplayback", false, 0, 99));
    }

    @Test
    public void followsLocalRedirectsWithHeadAndRangePreserved() throws Exception {
        final URL initial = new URL("https://example.org/videoplayback");
        final URL next = new URL("https://example.org/videoplayback?next");
        final HttpURLConnection first = mock(HttpURLConnection.class);
        when(first.getResponseCode()).thenReturn(302);
        when(first.getHeaderField("Location")).thenReturn("/videoplayback?next");
        final HttpURLConnection second = mock(HttpURLConnection.class);
        when(second.getResponseCode()).thenReturn(206);
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            connections.when(() -> AppProxySelector.openConnection(initial)).thenReturn(first);
            connections.when(() -> AppProxySelector.openConnection(next)).thenReturn(second);
            assertSame(second, mission().openConnection(initial.toString(), true, 100, 199));
            verify(first).setInstanceFollowRedirects(false);
            verify(second).setInstanceFollowRedirects(false);
            verify(first).setRequestMethod("HEAD");
            verify(second).setRequestMethod("HEAD");
            verify(first).setRequestProperty("Range", "bytes=100-199");
            verify(second).setRequestProperty("Range", "bytes=100-199");
            verify(first).disconnect();
        }
    }

    @Test
    public void checksRedirectBeforeOpeningTheDirectDestination() throws Exception {
        final HttpURLConnection first = mock(HttpURLConnection.class);
        when(first.getResponseCode()).thenReturn(302);
        when(first.getHeaderField("Location"))
                .thenReturn("https://rr1.googlevideo.com/videoplayback");
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            connections.when(() -> AppProxySelector.openConnection(any(URL.class)))
                    .thenReturn(first);
            assertThrows(IOException.class, () -> mission().openConnection(
                    "https://example.org/videoplayback", false, 0, -1));
            connections.verify(() -> AppProxySelector.openConnection(any(URL.class)), times(1));
            verify(first).disconnect();
        }
    }

    @Test
    public void rejectsRetiredInstancesAndUnknownMediaHostsBeforeTransport() throws Exception {
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            for (final String url : new String[]{"https://203.0.113.1/videoplayback",
                    "https://old.example.org/videoplayback", "http://example.org/videoplayback"}) {
                assertThrows(IOException.class, () -> mission().openConnection(url, false, 0, 99));
            }
            connections.verify(() -> AppProxySelector.openConnection(any(URL.class)), never());
        }
    }

}
