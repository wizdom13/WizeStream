package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class YoutubeStreamExtractorTest {
    @Test
    public void directStreamsPreferVisionOsAndKeepAndroidAsLastFallback()
            throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/extractor/services/youtube/extractors/"
                        + "YoutubeStreamExtractor.java"));
        final int helperStart = source.indexOf(
                "getPreferredStreamingData() {");
        final int helperEnd = source.indexOf(
                "private void ensureStreamsAreCached", helperStart);

        assertTrue(helperStart >= 0);
        assertTrue(helperEnd > helperStart);

        final String priority = source.substring(helperStart, helperEnd);
        final int visionOs = priority.indexOf(
                "new Pair<>(visionOsStreamingData, visionOsCpn)");
        final int safari = priority.indexOf(
                "new Pair<>(safariStreamingData, safariCpn)");
        final int ios = priority.indexOf(
                "new Pair<>(iosStreamingData, iosCpn)");
        final int tv = priority.indexOf(
                "new Pair<>(tvHtml5SimplyEmbedStreamingData, tvHtml5SimplyEmbedCpn)");
        final int android = priority.indexOf(
                "new Pair<>(androidStreamingData, androidCpn)");

        assertTrue(visionOs >= 0);
        assertTrue(visionOs < safari);
        assertTrue(safari < ios);
        assertTrue(ios < tv);
        assertTrue(tv < android);
        assertEquals(5, countOccurrences(source, "getPreferredStreamingData()"));
    }

    @Test
    public void playableMetadataCanUseConventionalStreamsFromAnotherClient()
            throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/extractor/services/youtube/extractors/"
                        + "YoutubeStreamExtractor.java"));

        assertTrue(source.contains("private JsonObject visionOsPlayerResponse;"));
        assertTrue(source.contains("visionOsPlayerResponse = getJsonVisionOsPostResponse("));
        assertTrue(source.contains("hasUsablePlaybackData(response)"
                + " || hasAnyUsablePlaybackData()"));
        assertTrue(source.contains("visionOsPlayerResponse, safariPlayerResponse"));
        assertTrue(source.contains("if (hasUsableStreamingData(streamingData)) {"
                + System.lineSeparator() + "                return false;"));
        assertTrue(source.contains("return foundSabrData;"));
    }

    @Test
    public void anonymousPlaybackUsesAndroidReelBeforeFallbacks() throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/extractor/services/youtube/extractors/"
                        + "YoutubeStreamExtractor.java"));
        final int fetchStart = source.indexOf("public void onFetchPage(");
        final int fetchEnd = source.indexOf(
                "private void waitForCallsToFinish", fetchStart);

        assertTrue(fetchStart >= 0);
        assertTrue(fetchEnd > fetchStart);

        final String fetchFlow = source.substring(fetchStart, fetchEnd);
        final int androidReel = fetchFlow.indexOf(
                "tryFetchAndroidReelJsonPlayer(contentCountry, localization, videoId)");
        final int visionOs = fetchFlow.indexOf(
                "tryFetchVisionOsJsonPlayer(contentCountry, localization, videoId)");

        assertTrue(androidReel >= 0);
        assertTrue(androidReel < visionOs);
        assertTrue(source.contains("\"reel/reel_item_watch\""));
        assertTrue(source.contains(".object(\"playerRequest\")"));
        assertTrue(source.contains(".value(\"disablePlayerResponse\", false)"));
        assertTrue(source.contains("&$fields=playerResponse"));
        assertTrue(source.contains("prepareAndroidMobileJsonBuilder("));
        assertTrue(source.contains("getJsonAndroidPostResponse("));
    }

    @Test
    public void muxed360pOnlyStillNeedsDirectStreamFallback() throws JsonParserException {
        final JsonObject reelStreamingData = JsonParser.object().from(
                "{\"formats\":[{\"itag\":18,\"url\":\"https://example.com/360p\"}],"
                        + "\"adaptiveFormats\":[{\"itag\":137}],"
                        + "\"serverAbrStreamingUrl\":\"https://example.com/sabr\"}");
        assertFalse(YoutubeStreamExtractor.hasUsableAdaptiveOrManifestData(
                reelStreamingData));

        final JsonObject adaptiveStreamingData = JsonParser.object().from(
                "{\"adaptiveFormats\":[{\"itag\":137,"
                        + "\"url\":\"https://example.com/1080p\"}]}");
        assertTrue(YoutubeStreamExtractor.hasUsableAdaptiveOrManifestData(
                adaptiveStreamingData));

        final JsonObject liveStreamingData = JsonParser.object().from(
                "{\"hlsManifestUrl\":\"https://example.com/live.m3u8\"}");
        assertTrue(YoutubeStreamExtractor.hasUsableAdaptiveOrManifestData(
                liveStreamingData));
    }

    @Test
    public void muxedPlaybackDoesNotSuppressQualityFallbacks() throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/extractor/services/youtube/extractors/"
                        + "YoutubeStreamExtractor.java"));
        final int fetchStart = source.indexOf("public void onFetchPage(");
        final int fetchEnd = source.indexOf(
                "private void waitForCallsToFinish", fetchStart);

        assertTrue(fetchStart >= 0);
        assertTrue(fetchEnd > fetchStart);
        assertEquals(5, countOccurrences(source.substring(fetchStart, fetchEnd),
                "needsDirectStreamFallback()"));
    }

    @Test
    public void diagnosticsIncludeVisionOsFallback() throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/extractor/services/youtube/extractors/"
                        + "YoutubeDiagnosticStreamExtractor.java"));

        assertTrue(source.contains("\"visionos\", \"visionOsPlayerResponse\""));
        assertTrue(source.contains("\"visionOsStreamingData\", requestedVideoId"));
    }

    private static int countOccurrences(final String source, final String value) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
