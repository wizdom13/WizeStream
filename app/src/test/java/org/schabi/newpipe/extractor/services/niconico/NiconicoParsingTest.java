package org.schabi.newpipe.extractor.services.niconico;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.niconico.linkHandler.NiconicoTrendLinkHandlerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class NiconicoParsingTest {
    @Test
    public void snapshotTimestampIsNormalizedToUtcByDateWrapper() {
        final DateWrapper parsed = NiconicoServiceParsingHelper.parseSnapshotDateTime(
                "2026-09-06T12:34:56+09:00");

        assertEquals(OffsetDateTime.parse("2026-09-06T03:34:56Z"),
                parsed.offsetDateTime());
    }

    @Test
    public void rssTimestampUsesJapanTimeThenNormalizesToUtc() {
        final DateWrapper parsed = NiconicoServiceParsingHelper.parseRSSDateTime(
                "2026年09月06日 12：34：56");

        assertEquals(OffsetDateTime.parse("2026-09-06T03:34:56Z"),
                parsed.offsetDateTime());
    }

    @Test
    public void masterPlaylistCollectsAudioAndVideoEntries() {
        final String master = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\","
                + "URI=\"https://cdn.example/audio.m3u8?token=a\"\n"
                + "https://cdn.example/video.m3u8?token=v#";

        final Map<String, List<String>> parsed = M3U8Parser.parseMasterM3U8(
                master, "user_session=abc", 1234L);

        assertEquals(List.of(
                "https://cdn.example/audio.m3u8?token=a#cookie=user_session%3Dabc&length=1234"),
                parsed.get("audio"));
        assertEquals(List.of(
                "https://cdn.example/video.m3u8?token=v#cookie=user_session%3Dabc&length=1234"),
                parsed.get("video"));
    }

    @Test
    public void masterPlaylistEncodesCookieBeforeAppendingTransportMetadata() {
        final String master = "#EXT-X-MEDIA:TYPE=AUDIO,"
                + "URI=\"https://cdn.example/audio.m3u8?token=a\"";

        final String audio = M3U8Parser.parseMasterM3U8(
                master, "session=a b+c", 99L).get("audio").get(0);

        assertTrue(audio.contains("cookie=session%3Da+b%2Bc"));
        assertTrue(audio.endsWith("&length=99"));
    }

    @Test
    public void masterPlaylistRetainsEveryVariantIncludingTheLastAtEndOfFile() {
        final String master = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000\n"
                + "https://cdn.example/video-low.m3u8?token=low\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2000000\n"
                + "https://cdn.example/video-high.m3u8?token=high";
        final List<String> expected = List.of(
                "https://cdn.example/video-low.m3u8?token=low#cookie=domand_bid%3Dabc&length=30",
                "https://cdn.example/video-high.m3u8?token=high#cookie=domand_bid%3Dabc&length=30");

        assertEquals(expected, M3U8Parser.parseMasterM3U8(master, "domand_bid=abc", 30)
                .get("video"));
        assertEquals(expected, M3U8Parser.parseMasterM3U8(
                master.replace("\n", ""), "domand_bid=abc", 30).get("video"));
    }

    @Test
    public void singleVariantWithoutQueryOrTrailingTagIsRetained() {
        assertEquals(List.of("https://cdn.example/video.m3u8#cookie=domand_bid%3Dabc&length=30"),
                M3U8Parser.parseMasterM3U8("#EXTM3U\nhttps://cdn.example/video.m3u8\n",
                        "domand_bid=abc", 30).get("video"));
    }

    @Test
    public void emptyMasterPlaylistProducesNoSyntheticTracks() {
        final Map<String, List<String>> parsed = M3U8Parser.parseMasterM3U8(
                "#EXTM3U\n#EXT-X-VERSION:3", "session=test", 0L);

        assertTrue(parsed.isEmpty());
        assertFalse(parsed.containsKey("audio"));
        assertFalse(parsed.containsKey("video"));
    }

    @Test
    public void liveDiscoveryKiosksMapToCurrentNiconicoEndpoints() throws Exception {
        final NiconicoTrendLinkHandlerFactory factory = new NiconicoTrendLinkHandlerFactory();

        assertEquals("Recommended Lives",
                factory.getId(NiconicoService.RECOMMEND_LIVES_URL));
        assertEquals("Top Lives",
                factory.getId(NiconicoService.TOP_LIVES_URL));
        assertEquals(NiconicoService.RECOMMEND_LIVES_URL,
                factory.getUrl("Recommended Lives", List.of(), List.of()));
        assertEquals(NiconicoService.TOP_LIVES_URL,
                factory.getUrl("Top Lives", List.of(), List.of()));
        assertTrue(NiconicoService.RECOMMEND_LIVES_URL.contains("/api/v2/"));
    }
}
