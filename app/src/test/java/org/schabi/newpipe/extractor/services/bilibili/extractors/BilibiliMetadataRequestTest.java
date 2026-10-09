package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.bilibili.WatchDataCache;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import okhttp3.Call;

/** Exercises the real metadata-to-playback path without live network requests. */
public class BilibiliMetadataRequestTest {
    private static final String BV = "BV17x411w7KC";
    private static final String METADATA_URL =
            "https://api.bilibili.com/x/web-interface/wbi/view?bvid=" + BV;
    private static final String METADATA = "{\"code\":0,\"data\":{\"title\":\"Fixture\","
            + "\"rights\":{\"pay\":0},\"pages\":[{\"cid\":11,\"duration\":60,\"page\":1,"
            + "\"part\":\"First\"},{\"cid\":22,\"duration\":90,\"page\":2,\"part\":\"Second\"}]}}";
    private static final String PLAYBACK = "{\"code\":0,\"data\":{\"dash\":{\"video\":[],"
            + "\"audio\":[{\"baseUrl\":\"https://media.example/audio.m4s\","
            + "\"base_url\":\"https://media.example/audio.m4s\",\"id\":30280,"
            + "\"codecs\":\"mp4a.40.2\",\"bandwidth\":192000,\"backupUrl\":[],"
            + "\"SegmentBase\":{\"Initialization\":\"0-99\",\"indexRange\":\"100-199\"}}]}}}";
    private final Downloader oldDownloader = NewPipe.getDownloader();
    private final Localization oldLocalization = NewPipe.getPreferredLocalization();
    private final ContentCountry oldCountry = NewPipe.getPreferredContentCountry();
    private final String oldTokens = ServiceList.BiliBili.getTokens();
    private final Set<String> oldFunctions = ServiceList.BiliBili.getCookieFunctions();
    private final List<Field> fields = new ArrayList<>();
    private final List<Object> oldValues = new ArrayList<>();
    private final List<Request> requests = new ArrayList<>();
    private int metadataStatus = 200;
    private String metadataBody = METADATA;

    @Before
    public void setUp() throws Exception {
        // Avoid real cookie/WBI bootstrap requests while retaining normal playback headers.
        final LinkedHashMap<String, String> cookies = new LinkedHashMap<>();
        cookies.put("bili_ticket_expires", Long.toString(Long.MAX_VALUE));
        cookies.put("bili_ticket", "fixture-default-ticket");
        replace(BilibiliService.class, "defaultLatestCookies", cookies);
        replace(utils.class, "wbiMixinKey", "0123456789abcdef0123456789abcdef");
        replace(utils.class, "wbiMixinKeyDate", LocalDate.now(ZoneId.of("Asia/Shanghai")));
        ServiceList.BiliBili.setTokens("");
        ServiceList.BiliBili.setCookieFunctions(Collections.emptySet());
        NewPipe.init(new Downloader() {
            @Override
            public Response execute(final Request request) {
                requests.add(request);
                if (request.url().equals(METADATA_URL)) {
                    return response(request, metadataStatus, metadataBody);
                }
                if (request.url().startsWith(BilibiliService.FREE_VIDEO_BASE_URL + "?")) {
                    return response(request, 200, PLAYBACK);
                }
                // The legacy endpoint would return risk control on the reporter's network.
                if (request.url().contains("/x/web-interface/view?")) {
                    return response(request, 412, "<html>security control policy</html>");
                }
                throw new AssertionError("Unexpected request: " + request.url());
            }

            @Override
            public CancellableCall executeAsync(final Request request,
                                                final AsyncCallback callback) {
                final CancellableCall call = new CancellableCall(mock(Call.class));
                call.setFinished();
                return call;
            }
        }, Localization.DEFAULT, ContentCountry.DEFAULT);
    }

    @After
    public void tearDown() throws Exception {
        NewPipe.init(oldDownloader, oldLocalization, oldCountry);
        ServiceList.BiliBili.setTokens(oldTokens);
        ServiceList.BiliBili.setCookieFunctions(oldFunctions);
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).set(null, oldValues.get(i));
        }
    }

    @Test
    public void publicMetadataRequestLoadsPlayableAudioWithoutLegacyEndpoint() throws Exception {
        final BillibiliStreamExtractor extractor = extractor(1);
        extractor.fetchPage();

        assertEquals(11, extractor.cid);
        assertEquals(60, extractor.getLength());
        assertEquals("https://media.example/audio.m4s",
                extractor.getAudioStreams().get(0).getContent());
        assertEquals(2, requests.size());
        assertEquals(METADATA_URL, requests.get(0).url());
        assertEquals(Collections.singletonList(""), requests.get(0).headers().get("Cookie"));
        assertFalse(requests.get(0).headers().containsKey("Referer"));
    }

    @Test
    public void keepsSelectedPartAndAuthenticatedPlaybackCookies() throws Exception {
        ServiceList.BiliBili.setTokens("SESSDATA=fixture-session");
        ServiceList.BiliBili.setCookieFunctions(Set.of("high_res", "ai_subtitle"));
        final BillibiliStreamExtractor extractor = extractor(2);
        extractor.fetchPage();

        assertEquals(22, extractor.cid);
        assertEquals(90, extractor.getLength());
        assertEquals(Collections.singletonList(""), requests.get(0).headers().get("Cookie"));
        assertEquals(Collections.singletonList("SESSDATA=fixture-session"),
                requests.get(1).headers().get("Cookie"));
        assertTrue(requests.get(1).url().contains("cid=22"));
    }

    @Test
    public void stillReportsRiskControlWithoutLoadingPlayback() {
        metadataStatus = 412;
        metadataBody = "<html>private security response</html>";
        assertThrows(ServiceTemporaryBlockedException.class, () -> extractor(1).fetchPage());
        assertEquals(1, requests.size());

        metadataStatus = 200;
        metadataBody = "{\"code\":-352,\"data\":{\"v_voucher\":\"private\"}}";
        assertThrows(ServiceTemporaryBlockedException.class, () -> extractor(1).fetchPage());
        assertEquals(2, requests.size());
    }

    @Test
    public void rejectsMissingMetadataAndUnavailablePartsBeforePlayback() {
        metadataBody = "{\"code\":-404,\"message\":\"Video unavailable\",\"data\":{}}";
        final ContentNotAvailableException error = assertThrows(
                ContentNotAvailableException.class, () -> extractor(1).fetchPage());
        assertEquals("Video unavailable", error.getMessage());
        metadataBody = "{\"code\":0,\"data\":{}}";
        assertThrows(ContentNotAvailableException.class, () -> extractor(1).fetchPage());
        metadataBody = METADATA;
        assertThrows(ContentNotAvailableException.class, () -> extractor(3).fetchPage());
        assertEquals(3, requests.size());
    }

    private BillibiliStreamExtractor extractor(final int part) throws Exception {
        return new BillibiliStreamExtractor(ServiceList.BiliBili,
                ServiceList.BiliBili.getStreamLHFactory().fromUrl(
                        "https://www.bilibili.com/video/" + BV + "?p=" + part),
                new WatchDataCache());
    }

    private void replace(final Class<?> type, final String name, final Object value)
            throws Exception {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        fields.add(field);
        oldValues.add(field.get(null));
        field.set(null, value);
    }

    private static Response response(final Request request, final int status, final String body) {
        return new Response(status, "fixture", Collections.emptyMap(), body,
                body.getBytes(StandardCharsets.UTF_8), request.url());
    }
}
