package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class CustomRenderersFactorySubtitleTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void sidecarSubtitlesKeepLegacyRendererDecodingEnabled() throws Exception {
        final String rendererFactory = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/helper/CustomRenderersFactory.java"));
        final String playerDataSource = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/helper/PlayerDataSource.java"));

        assertTrue(rendererFactory.contains("buildTextRenderers"));
        assertTrue(rendererFactory.contains("experimentalSetLegacyDecodingEnabled(true)"));
        assertTrue(playerDataSource.contains("new SingleSampleMediaSource.Factory"));
    }
}
