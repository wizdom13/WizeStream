package org.schabi.newpipe.player;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class LearningNoteButtonLifecycleTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void coldStartupRefreshesButtonWhenMetadataArrives() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/ui/MainPlayerUi.java"));

        assertTrue(methodBody(source, "protected void setupElementsVisibility()")
                .contains("updateLearningNoteButtonVisibility("
                        + "player.getCurrentStreamInfo().orElse(null));"));
        assertTrue(methodBody(source,
                "public void onMetadataChanged(@NonNull final StreamInfo info)")
                .contains("updateLearningNoteButtonVisibility(info);"));
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
