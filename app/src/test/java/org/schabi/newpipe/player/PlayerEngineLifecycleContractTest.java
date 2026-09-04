package org.schabi.newpipe.player;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PlayerEngineLifecycleContractTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void kotlinPlayerPreservesNullableJavaExoPlayerAccess() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/Player.kt"));

        assertTrue(source.contains("@get:JvmName(\"requireExoPlayer\")"));
        assertTrue(source.contains("fun getExoPlayer(): ExoPlayer? = media3Player"));
    }

    @Test
    public void destroyingPlayerClearsReleasedEngineReference() throws Exception {
        final String playerSource = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/Player.kt"));
        final String lifecycleSource = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/PlayerLifecycleController.kt"));

        assertTrue(methodBody(playerSource, "fun clearExoPlayerForLifecycle()")
                .contains("media3Player = null"));
        assertTrue(methodBody(lifecycleSource, "private fun destroyPlayer()")
                .contains("player.clearExoPlayerForLifecycle()"));
    }

    private static String methodBody(final String source, final String signature) {
        final int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method: " + signature, signatureIndex >= 0);

        final int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body: " + signature, bodyStart >= 0);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("Unclosed method body: " + signature);
    }
}
