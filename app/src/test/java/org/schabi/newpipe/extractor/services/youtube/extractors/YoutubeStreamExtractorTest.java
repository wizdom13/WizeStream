package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
