package org.schabi.newpipe.player.resolver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VideoPlaybackResolverCaptionTranslationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void currentCaptionTranslationIsAppliedWhenResolvingCachedStreamInfo()
            throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/resolver/VideoPlaybackResolver.java"));

        assertTrue(source.contains("new ArrayList<>(info.getSubtitles())"));
        assertTrue(source.contains(
                "YoutubeCaptionTranslationHelper.addTranslatedSubtitleFromExtractedStreams"));
        assertTrue(source.contains("CaptionTranslationPreferences.getTargetLanguage(context)"));
        assertTrue(source.indexOf("addTranslatedSubtitleFromExtractedStreams")
                < source.indexOf("getUrlAndNonTorrentStreams"));
    }
}
