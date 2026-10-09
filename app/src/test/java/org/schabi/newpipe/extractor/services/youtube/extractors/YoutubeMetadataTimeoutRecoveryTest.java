package org.schabi.newpipe.extractor.services.youtube.extractors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.WatchDataCache;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/** Runs the real client-selection and app metadata-recovery paths with fixture HTTP responses. */
public class YoutubeMetadataTimeoutRecoveryTest {
    private static final String VIDEO = "LWQUdKwde74";
    private static final String HLS = "https://manifest.googlevideo.com/live/test.m3u8";
    private static final String PLAYER = "{\"playabilityStatus\":{\"status\":\"OK\","
            + "\"liveStreamability\":{}},\"videoDetails\":{\"videoId\":\"" + VIDEO
            + "\",\"title\":\"Live fixture\",\"author\":\"Channel\","
            + "\"shortDescription\":\"Player description\"},\"streamingData\":{"
            + "\"hlsManifestUrl\":\"" + HLS + "\"}}";
    private final Downloader oldDownloader = NewPipe.getDownloader();
    private final Localization oldLocalization = NewPipe.getPreferredLocalization();
    private final ContentCountry oldCountry = NewPipe.getPreferredContentCountry();
    private final String oldTokens = ServiceList.YouTube.getTokens();
    private final boolean oldDislike = ServiceList.YouTube.isFetchDislike();
    private Field versionField;
    private Object oldVersion;
    private int visitorRequests;
    private int metadataRetries;
    private int asyncMetadataRequests;
    private boolean retryFails;

    @Before
    public void setUp() throws Exception {
        versionField = YoutubeParsingHelper.class.getDeclaredField("clientVersion");
        versionField.setAccessible(true);
        oldVersion = versionField.get(null);
        versionField.set(null, "2.20261009.01.00");
        ServiceList.YouTube.setTokens("");
        ServiceList.YouTube.setFetchDislike(false);
        NewPipe.init(new Downloader() {
            @Override
            public Response execute(final Request request) throws IOException {
                if (request.url().contains("/visitor_id?")) {
                    visitorRequests++;
                    if (visitorRequests <= 2) {
                        throw new SocketTimeoutException("fixture visitor timeout");
                    }
                    return response(request, "{\"responseContext\":{"
                            + "\"visitorData\":\"fixture-visitor-data\"}}");
                }
                if (request.url().contains("/next?")) {
                    metadataRetries++;
                    if (retryFails) {
                        throw new SocketTimeoutException("fixture metadata retry timeout");
                    }
                    return response(request, "{\"contents\":{},\"attributedDescription\":{"
                            + "\"content\":\"Recovered watch-next description\"}}");
                }
                throw new AssertionError("Unexpected synchronous request: " + request.url());
            }

            @Override
            public CancellableCall executeAsync(final Request request,
                                                final AsyncCallback callback) {
                try {
                    if (request.url().contains("/next?")) {
                        asyncMetadataRequests++;
                        callback.onError(new SocketTimeoutException("fixture metadata timeout"));
                    } else if (request.url().contains("/player?")) {
                        callback.onSuccess(response(request, PLAYER));
                    } else {
                        throw new AssertionError("Unexpected async request: " + request.url());
                    }
                } catch (final ExtractionException error) {
                    throw new AssertionError(error);
                }
                final CancellableCall call = new CancellableCall(mock(Call.class));
                call.setFinished();
                return call;
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        versionField.set(null, oldVersion);
        ServiceList.YouTube.setTokens(oldTokens);
        ServiceList.YouTube.setFetchDislike(oldDislike);
        NewPipe.init(oldDownloader, oldLocalization, oldCountry);
    }

    @Test
    public void selectedAndroidStreamReachesMetadataRetryAfterEarlierClientTimeouts()
            throws Exception {
        final YoutubeDiagnosticStreamExtractor extractor = extractor();
        extractor.fetchPage();
        assertPlayback(extractor);
        assertEquals(3, visitorRequests);
        assertEquals(1, asyncMetadataRequests);
        assertEquals(1, metadataRetries);
        assertFalse(extractor.errors.isEmpty());
        // Earlier failed client requests remain available for diagnostics rather than fatal.
        assertTrue(extractor.errors.get(0).getCause() instanceof SocketTimeoutException);
    }

    @Test
    public void failedMetadataRetryStillLeavesPlayableStreamAndSafeOptionalGetters()
            throws Exception {
        retryFails = true;
        final YoutubeDiagnosticStreamExtractor extractor = extractor();
        extractor.fetchPage();
        assertPlayback(extractor);
        assertEquals(1, metadataRetries);
        assertTrue(extractor.getRelatedItems().getItems().isEmpty());
        assertFalse(extractor.getDescription().getContent().isEmpty());
        assertTrue(extractor.errors.stream().anyMatch(error -> error.getMessage()
                .contains("watch-next metadata recovery")
                && error.getCause() instanceof SocketTimeoutException));
    }

    private void assertPlayback(final YoutubeDiagnosticStreamExtractor extractor)
            throws Exception {
        assertEquals("Live fixture", extractor.getName());
        assertEquals("Channel", extractor.getUploaderName());
        assertEquals(StreamType.LIVE_STREAM, extractor.getStreamType());
        assertEquals(HLS, extractor.getHlsUrl());
    }

    private YoutubeDiagnosticStreamExtractor extractor() throws Exception {
        return new YoutubeDiagnosticStreamExtractor(ServiceList.YouTube,
                ServiceList.YouTube.getStreamLHFactory()
                        .fromUrl("https://www.youtube.com/watch?v=" + VIDEO),
                new WatchDataCache());
    }

    private Response response(final Request request, final String body) {
        return new Response(200, "fixture", Map.of("Content-Type", List.of("application/json")),
                body, body.getBytes(StandardCharsets.UTF_8), request.url());
    }
}
