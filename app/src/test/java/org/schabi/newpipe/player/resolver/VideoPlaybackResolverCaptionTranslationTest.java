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

        final int subtitleBlock = source.indexOf("// Create subtitle sources.");
        final int translationCall = source.indexOf(
                "addTranslatedSubtitleFromExtractedStreams", subtitleBlock);
        final int filteringCall = source.indexOf("getUrlAndNonTorrentStreams(", subtitleBlock);
        assertTrue(subtitleBlock >= 0);
        assertTrue(translationCall > subtitleBlock);
        assertTrue(filteringCall > translationCall);
    }
}
