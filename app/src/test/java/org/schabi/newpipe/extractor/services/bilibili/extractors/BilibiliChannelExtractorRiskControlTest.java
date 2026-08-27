package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.USER_VIDEO_API_MODE_CLIENT;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.USER_VIDEO_API_MODE_SEARCH;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.USER_VIDEO_API_MODE_WEB;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getCurrentVideoApiMode;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.setCurrentVideoApiMode;

import com.grack.nanojson.JsonObject;

import org.junit.After;
import org.junit.Test;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BilibiliChannelExtractorRiskControlTest {

    private static final String URL = "https://api.bilibili.com/test";

    @After
    public void resetPreferredMode() {
        setCurrentVideoApiMode(USER_VIDEO_API_MODE_WEB);
    }

    @Test
    public void recognizesHttp412WithLeadingBomAndWhitespace() throws Exception {
        final String body = "\uFEFF  \n<!DOCTYPE html><html>ordinary response</html>";
        final ServiceTemporaryBlockedException exception = assertThrows(
                ServiceTemporaryBlockedException.class,
                () -> BilibiliChannelExtractor.requestUserSpaceResponse(
                        downloaderReturning(412, body), URL, Collections.emptyMap())
        );

        assertEquals("BiliBili temporarily blocked requests from this network",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("<!DOCTYPE"));
    }

    @Test
    public void recognizesSecurityHtmlReturnedWithSuccessStatus() throws Exception {
        final String body = "\uFEFF \n<!DOCTYPE html><html>"
                + "<script src=\"//security.bilibili.com/static/js/412.js\"></script>"
                + "</html>";

        assertThrows(
                ServiceTemporaryBlockedException.class,
                () -> BilibiliChannelExtractor.requestUserSpaceResponse(
                        downloaderReturning(200, body), URL, Collections.emptyMap())
        );
    }

    @Test
    public void recognizesJsonRiskControlCode() throws Exception {
        final String body = "{\"code\":-352,\"message\":\"risk control\","
                + "\"data\":{\"v_voucher\":\"private-value\"}}";
        setCurrentVideoApiMode(USER_VIDEO_API_MODE_SEARCH);
        final ServiceTemporaryBlockedException exception = assertThrows(
                ServiceTemporaryBlockedException.class,
                () -> BilibiliChannelExtractor.requestUserSpaceResponse(
                        downloaderReturning(200, body), URL, Collections.emptyMap())
        );

        assertFalse(exception.getMessage().contains("private-value"));
        assertEquals(USER_VIDEO_API_MODE_SEARCH, getCurrentVideoApiMode());
    }

    @Test
    public void returnsSuccessfulJsonResponse() throws Exception {
        final JsonObject response = BilibiliChannelExtractor.requestUserSpaceResponse(
                downloaderReturning(200, "{\"code\":0,\"data\":{\"name\":\"ok\"}}"),
                URL,
                Collections.emptyMap()
        );

        assertEquals("ok", response.getObject("data").getString("name"));
    }

    @Test
    public void sanitizesUnexpectedNonJsonResponse() throws Exception {
        final String body = "<html>not a BiliBili security response</html>";
        final ParsingException exception = assertThrows(
                ParsingException.class,
                () -> BilibiliChannelExtractor.requestUserSpaceResponse(
                        downloaderReturning(500, body), URL, Collections.emptyMap())
        );

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertFalse(exception.getMessage().contains(body));
    }

    @Test
    public void fallsBackAcrossEachVideoApiOnceAndKeepsSuccessfulMode() throws Exception {
        final List<Integer> attemptedModes = new ArrayList<>();

        final int selectedMode = BilibiliChannelExtractor.runVideoApiFallback(
                USER_VIDEO_API_MODE_WEB,
                mode -> {
                    attemptedModes.add(mode);
                    if (mode != USER_VIDEO_API_MODE_CLIENT) {
                        throw blocked();
                    }
                }
        );

        assertEquals(List.of(
                USER_VIDEO_API_MODE_WEB,
                USER_VIDEO_API_MODE_SEARCH,
                USER_VIDEO_API_MODE_CLIENT
        ), attemptedModes);
        assertEquals(USER_VIDEO_API_MODE_CLIENT, selectedMode);
        assertEquals(USER_VIDEO_API_MODE_CLIENT, getCurrentVideoApiMode());
    }

    @Test
    public void stopsAfterAllVideoApisAreBlocked() {
        final List<Integer> attemptedModes = new ArrayList<>();

        assertThrows(
                ServiceTemporaryBlockedException.class,
                () -> BilibiliChannelExtractor.runVideoApiFallback(
                        USER_VIDEO_API_MODE_SEARCH,
                        mode -> {
                            attemptedModes.add(mode);
                            throw blocked();
                        }
                )
        );

        assertEquals(List.of(
                USER_VIDEO_API_MODE_SEARCH,
                USER_VIDEO_API_MODE_CLIENT,
                USER_VIDEO_API_MODE_WEB
        ), attemptedModes);
        assertEquals(USER_VIDEO_API_MODE_CLIENT, getCurrentVideoApiMode());
    }

    @Test
    public void doesNotFallbackForOrdinaryParsingFailures() {
        final List<Integer> attemptedModes = new ArrayList<>();

        assertThrows(
                ParsingException.class,
                () -> BilibiliChannelExtractor.runVideoApiFallback(
                        USER_VIDEO_API_MODE_WEB,
                        mode -> {
                            attemptedModes.add(mode);
                            throw new ParsingException("unexpected schema");
                        }
                )
        );

        assertEquals(List.of(USER_VIDEO_API_MODE_WEB), attemptedModes);
    }

    private static ServiceTemporaryBlockedException blocked() {
        return new ServiceTemporaryBlockedException("temporarily blocked");
    }

    private static Downloader downloaderReturning(final int responseCode, final String body)
            throws Exception {
        final Response response = new Response(
                responseCode,
                "response",
                Collections.emptyMap(),
                body,
                body.getBytes(StandardCharsets.UTF_8),
                URL
        );
        return new Downloader() {
            @Override
            public Response execute(final Request request) {
                return response;
            }

            @Override
            public CancellableCall executeAsync(
                    final Request request,
                    final AsyncCallback callback
            ) {
                throw new UnsupportedOperationException("Async requests are not used in this test");
            }
        };
    }
}
