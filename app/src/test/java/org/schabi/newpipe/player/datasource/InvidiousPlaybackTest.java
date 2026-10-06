package org.schabi.newpipe.player.datasource;

import android.app.Application;
import android.net.Uri;

import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.HttpDataSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.network.AppProxySelector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class InvidiousPlaybackTest {
    @Before
    public void enable() {
        InvidiousBackend.configure(true, "https://example.org");
    }
    @After
    public void disable() {
        InvidiousBackend.configure(false, "");
    }

    @Test
    public void rejectsStaleDirectMediaBeforeOpeningAConnection() throws Exception {
        final var source = new YoutubeHttpDataSource.Factory().createDataSource();
        assertThrows(HttpDataSource.HttpDataSourceException.class, () -> source.open(
                new DataSpec(Uri.parse("https://rr1.googlevideo.com/videoplayback"))));
        assertThrows(IOException.class, () -> AppProxySelector.openConnection(
                new URL("https://rr1.googlevideo.com/videoplayback")));
    }

    @Test
    public void usesGetAndRangeHeadersAndFollowsOnlyLocalRedirects() throws Exception {
        final URL initial = new URL("https://example.org/videoplayback?initial");
        final URL redirected = new URL("https://example.org/videoplayback?next");
        final HttpURLConnection first = mock(HttpURLConnection.class);
        when(first.getResponseCode()).thenReturn(302);
        when(first.getHeaderField("Location")).thenReturn(redirected.toString());
        final HttpURLConnection second = mock(HttpURLConnection.class);
        when(second.getResponseCode()).thenReturn(206);
        when(second.getResponseMessage()).thenReturn("Partial Content");
        when(second.getHeaderField("Content-Length")).thenReturn("3");
        when(second.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            connections.when(() -> AppProxySelector.openConnection(initial)).thenReturn(first);
            connections.when(() -> AppProxySelector.openConnection(redirected)).thenReturn(second);
            final var source = new YoutubeHttpDataSource.Factory().createDataSource();
            assertEquals(3, source.open(new DataSpec.Builder().setUri(Uri.parse(initial.toString()))
                    .setPosition(100).setLength(3).build()));
            assertEquals(3, source.read(new byte[3], 0, 3));
            source.close();
            verify(first).setRequestMethod("GET");
            verify(second).setRequestMethod("GET");
            verify(first).setRequestProperty("Range", "bytes=100-102");
            verify(second).setRequestProperty("Range", "bytes=100-102");
            verify(first).setInstanceFollowRedirects(false);
            verify(second).setInstanceFollowRedirects(false);
            verify(first, never()).getOutputStream();
            verify(second, never()).getOutputStream();
            connections.verify(() -> AppProxySelector.openConnection(initial));
            connections.verify(() -> AppProxySelector.openConnection(redirected));
        }
    }

    @Test
    public void rejectsDirectRedirectsBeforeOpeningTheirDestination() throws Exception {
        final HttpURLConnection first = mock(HttpURLConnection.class);
        when(first.getResponseCode()).thenReturn(302);
        when(first.getHeaderField("Location"))
                .thenReturn("https://rr1.googlevideo.com/videoplayback");
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            connections.when(() -> AppProxySelector.openConnection(any(URL.class)))
                    .thenReturn(first);
            final var source = new YoutubeHttpDataSource.Factory().createDataSource();
            assertThrows(HttpDataSource.HttpDataSourceException.class,
                    () -> source.open(new DataSpec(Uri
                            .parse("https://example.org/videoplayback"))));
            connections.verify(() -> AppProxySelector.openConnection(any(URL.class)), times(1));
            verify(first).disconnect();
        }
    }

    @Test
    public void rejectsExternalManifestSegmentsKeysAndCaptionsBeforeTransport() throws Exception {
        final DataSource transport = mock(DataSource.class);
        final DataSource scoped = InvidiousDataSource.restrict(() -> transport).createDataSource();
        for (final String url : new String[]{"https://cdn.example.org/segment.ts",
                "https://203.0.113.1/segment.ts", "http://example.org/segment.ts",
                "https://rr1.googlevideo.com/segment.ts", "https://different.example.org/key",
                "https://different.example.org/captions"}) {
            assertThrows(IOException.class, () -> scoped.open(new DataSpec(Uri.parse(url))));
        }
        verify(transport, never()).open(any());
    }

    @Test
    public void allowsLocalSegmentsAndOfflineFilesAndRejectsAnInstanceSwitch() throws Exception {
        final DataSource transport = mock(DataSource.class);
        when(transport.open(any())).thenReturn(3L);
        final DataSource scoped = InvidiousDataSource.restrict(() -> transport).createDataSource();
        assertEquals(3, scoped.open(new DataSpec(Uri.parse("https://example.org/segment.ts"))));
        assertEquals(3, scoped.open(new DataSpec(Uri.parse("file:///storage/video.mp4"))));
        InvidiousBackend.configure(true, "https://changed.example.org");
        assertThrows(IOException.class,
                () -> scoped.open(new DataSpec(Uri.parse("https://example.org/segment.ts"))));
        verify(transport, times(2)).open(any());
    }

    @Test
    public void rejectsHtmlAndJsonMediaResponsesBeforeDecoding() throws Exception {
        for (final String mime : new String[]{"text/html; charset=UTF-8", "application/json",
                "application/problem+json"}) {
            assertRejectedResponse(mime, "<html>Site unavailable</html>");
        }
    }

    @Test
    public void rejectsErrorBodiesWithMissingOrMisleadingContentTypes() throws Exception {
        assertRejectedResponse(null, "\uFEFF  <!DOCTYPE HTML><html>Site unavailable</html>");
        assertRejectedResponse("video/mp4", "<html>Companion error</html>");
        assertRejectedResponse("application/octet-stream", "{\"error\":\"Companion unavailable\"}");
    }

    private static void assertRejectedResponse(final String mime, final String body)
            throws Exception {
        final URL url = new URL("https://example.org/videoplayback?token=private");
        final HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentType()).thenReturn(mime);
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            connections.when(() -> AppProxySelector.openConnection(url)).thenReturn(connection);
            final var source = new YoutubeHttpDataSource.Factory().createDataSource();
            final var error = assertThrows(InvidiousMediaResponse.InvalidResponseException.class,
                    () -> source.open(new DataSpec(Uri.parse(url.toString()))));
            org.junit.Assert.assertTrue(error.getMessage().contains("Invidious"));
            org.junit.Assert.assertFalse(error.getMessage().contains("private"));
            verify(connection).disconnect();
            source.close();
            connections.verify(() -> AppProxySelector.openConnection(url));
        }
    }

    @Test
    public void preservesMediaManifestAndCaptionBytesAndAllowsMidStreamRanges() throws Exception {
        for (final String body : new String[]{"\u0000\u0000\u0000\u0018ftypmp42",
                "#EXTM3U\n#EXT-X-VERSION:3", "<?xml version='1.0'?><MPD/>",
                "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nCaption"}) {
            final byte[] expected = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            final DataSpec spec = new DataSpec(Uri.parse("https://example.org/stream"));
            final var input = InvidiousMediaResponse.checkBody(
                    new ByteArrayInputStream(expected), spec);
            org.junit.Assert.assertArrayEquals(expected, input.readAllBytes());
        }
        final DataSpec range = new DataSpec.Builder().setUri("https://example.org/stream")
                .setPosition(100).build();
        final byte[] middle = "<html>arbitrary middle-of-media bytes".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        org.junit.Assert.assertArrayEquals(middle, InvidiousMediaResponse.checkBody(
                new ByteArrayInputStream(middle), range).readAllBytes());
    }

    @Test
    public void directBackendDoesNotApplyInvidiousResponsePolicy() throws Exception {
        InvidiousBackend.configure(false, "");
        final URL url = new URL("https://example.org/stream");
        final HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentType()).thenReturn("text/html");
        when(connection.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        try (MockedStatic<AppProxySelector> connections = mockStatic(AppProxySelector.class)) {
            connections.when(() -> AppProxySelector.openConnection(url)).thenReturn(connection);
            final var source = new YoutubeHttpDataSource.Factory().createDataSource();
            source.open(new DataSpec(Uri.parse(url.toString())));
            assertEquals(3, source.read(new byte[3], 0, 3));
            source.close();
        }
    }

}
