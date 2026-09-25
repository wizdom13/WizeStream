package org.schabi.newpipe.extractor.services.rumble;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RumbleParsingHelperTest {
    private static final String URL = "https://rumble.com/v-test.html";

    @Test
    public void removesTrackingQueryBeforeFetchingRumblePage() {
        assertEquals(
                "https://rumble.com/v7fudk0-hayden-panettiere-cause-of-death"
                        + "-alan-ritchson-blackmailed-she-hulk-loves-p.html",
                RumbleParsingHelper.removeQueryAndFragment(
                        "https://rumble.com/v7fudk0-hayden-panettiere-cause-of-death"
                                + "-alan-ritchson-blackmailed-she-hulk-loves-p.html"
                                + "?e9s=src_v1_epp#comments"));
    }

    @Test
    public void providesMinimalAnonymousSessionCookieForAllRumblePages() {
        final Map<String, List<String>> headers = RumbleParsingHelper.getMinimalHeaders();

        assertTrue(headers.containsKey("Cookie"));
        assertFalse(headers.get("Cookie").isEmpty());
        assertTrue(headers.get("Cookie").get(0)
                .matches("PNRC=\\d+ ; RNRC=\\d+"));
    }

    @Test
    public void parsesRelatedStreamDurationsAcrossSupportedShapes() throws Exception {
        assertEquals(45,
                RumbleParsingHelper.parseDurationStringForRelatedStreams("0:45"));
        assertEquals(3_723,
                RumbleParsingHelper.parseDurationStringForRelatedStreams("1:02:03"));
        assertEquals(93_784,
                RumbleParsingHelper.parseDurationString("1d 02h 03m 04s", "\\D+"));
    }

    @Test
    public void rejectsUnknownDurationShape() {
        assertThrows(ParsingException.class,
                () -> RumbleParsingHelper.parseDurationString("1:2:3:4:5", ":"));
    }

    @Test
    public void extractSafelyReturnsNullForOptionalMetadataFailure() throws Exception {
        assertEquals("ok", RumbleParsingHelper.extractSafely(false, "unused", () -> "ok"));
        assertNull(RumbleParsingHelper.extractSafely(false, "optional", () -> {
            throw new IllegalStateException("missing");
        }));
    }

    @Test
    public void extractSafelyWrapsRequiredMetadataFailures() {
        final ParsingException error = assertThrows(ParsingException.class,
                () -> RumbleParsingHelper.extractSafely(true, "required metadata", () -> {
                    throw new IllegalStateException("missing");
                }));
        assertTrue(error.getMessage().contains("required metadata"));
    }

    @Test
    public void extractsEmbedIdFromScriptAndCachesIt() throws Exception {
        final String pageUrl = "https://rumble.com/v-cache-test.html";
        final String html =
                "<script src=\"https://rumble.com/embed/vabc123.xyz789/\"></script>";

        assertEquals("yz789", RumbleParsingHelper.getEmbedVideoId(pageUrl, () -> html));
        assertEquals("yz789", RumbleParsingHelper.getEmbedVideoId(pageUrl, () -> {
            throw new AssertionError("cached embed id should avoid re-reading page content");
        }));
    }

    @Test
    public void privateForbiddenPageMapsToPrivateContent() {
        final Document doc = Jsoup.parse(
                "<html><head><title>Private video</title></head></html>", URL);
        assertThrows(PrivateContentException.class,
                () -> RumbleParsingHelper.checkIfContentIsAccessible(
                        response(403, "private"), doc));
    }

    @Test
    public void ordinaryMissingPageMapsToContentNotAvailable() {
        final Document doc = Jsoup.parse(
                "<html><head><title>Video not found</title></head></html>", URL);
        final ContentNotAvailableException error = assertThrows(
                ContentNotAvailableException.class,
                () -> RumbleParsingHelper.checkIfContentIsAccessible(
                        response(404, "missing"), doc));
        assertTrue(error.getMessage().contains("404"));
    }

    private static Response response(final int code, final String body) {
        return new Response(
                code,
                "response",
                Collections.emptyMap(),
                body,
                body.getBytes(StandardCharsets.UTF_8),
                URL
        );
    }
}
