package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VideoPlayerUiAspectRatioTransitionTest {
    private final Path mainDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void playbackBlockClearsThePreviousVideoAspectRatio() throws Exception {
        final String source = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/ui/VideoPlayerUi.java"));
        final int blockedMethodStart = source.indexOf("public void onBlocked()");
        final int playingMethodStart = source.indexOf(
                "public void onPlaying()", blockedMethodStart);

        assertTrue(blockedMethodStart >= 0);
        assertTrue(playingMethodStart > blockedMethodStart);
        assertTrue(source.substring(blockedMethodStart, playingMethodStart)
                .contains("binding.surfaceView.clearAspectRatio()"));
    }

    @Test
    public void cachedPreviousQueueItemAlsoClearsThePreviousVideoAspectRatio() throws Exception {
        final String playerSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/Player.java"));
        final String uiSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/ui/VideoPlayerUi.java"));

        final int itemChangeStart = playerSource.indexOf(
                "if (newIndex != oldPosition.mediaItemIndex)");
        final int discontinuitySwitchStart = playerSource.indexOf(
                "switch (discontinuityReason)", itemChangeStart);
        assertTrue(itemChangeStart >= 0);
        assertTrue(discontinuitySwitchStart > itemChangeStart);
        assertTrue(playerSource.substring(itemChangeStart, discontinuitySwitchStart)
                .contains("UIs.call(PlayerUi::onMediaItemTransition)"));
        final int transitionMethodStart = uiSource.indexOf(
                "public void onMediaItemTransition()");
        final int playingMethodStart = uiSource.indexOf(
                "public void onPlaying()", transitionMethodStart);
        assertTrue(transitionMethodStart >= 0);
        assertTrue(playingMethodStart > transitionMethodStart);
        assertTrue(uiSource.substring(transitionMethodStart, playingMethodStart)
                .contains("binding.surfaceView.clearAspectRatio()"));
    }

    @Test
    public void anamorphicVideoUsesItsDisplayPixelRatio() {
        assertEquals(16.0f / 9.0f,
                VideoPlayerUi.calculateDisplayAspectRatio(1440, 1080, 0, 4.0f / 3.0f),
                0.0001f);
    }

    @Test
    public void unappliedQuarterTurnInvertsTheDisplayAspectRatio() {
        assertEquals(9.0f / 16.0f,
                VideoPlayerUi.calculateDisplayAspectRatio(1440, 1080, 90, 4.0f / 3.0f),
                0.0001f);
        assertEquals(9.0f / 16.0f,
                VideoPlayerUi.calculateDisplayAspectRatio(1440, 1080, -90, 4.0f / 3.0f),
                0.0001f);
    }

    @Test
    public void invalidDimensionsAreIgnoredAndInvalidPixelRatioFallsBackToSquarePixels() {
        assertEquals(0.0f,
                VideoPlayerUi.calculateDisplayAspectRatio(0, 1080, 0, 1.0f), 0.0f);
        assertEquals(16.0f / 9.0f,
                VideoPlayerUi.calculateDisplayAspectRatio(1920, 1080, 0, Float.NaN),
                0.0001f);
    }
}
