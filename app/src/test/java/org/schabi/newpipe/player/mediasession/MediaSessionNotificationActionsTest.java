package org.schabi.newpipe.player.mediasession;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MediaSessionNotificationActionsTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void androidSystemUiGetsCustomActionCommandsAndPreferences() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/mediasession/MediaSessionPlayerUi.java"));
        final String updateActions = methodBody(source,
                "private void updateMediaSessionActions()");
        final String syncController = methodBody(source,
                "private void syncMediaNotificationController(");

        assertTrue(updateActions.contains("syncMediaNotificationController(buttons);"));
        assertTrue(syncController.contains("mediaSession.getMediaNotificationControllerInfo()"));
        assertTrue(syncController.contains("MediaSessionActionProvider.supportedActions()"));
        assertTrue(syncController.contains("mediaSession.setAvailableCommands("));
        assertTrue(syncController.contains(
                "mediaSession.setMediaButtonPreferences(notificationController, buttons);"));
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
