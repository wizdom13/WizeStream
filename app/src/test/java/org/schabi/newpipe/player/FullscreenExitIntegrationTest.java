package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FullscreenExitIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void fullscreenSwipeUsesOrientationAwarePlayerAction() throws Exception {
        final String source = read(
                "org/schabi/newpipe/player/gesture/MainPlayerGestureListener.kt");
        final String onScrollEnd = methodBody(source, "override fun onScrollEnd");

        assertTrue(onScrollEnd.contains("playerUi.toggleFullscreenWithOrientation()"));
        assertFalse(onScrollEnd.contains("playerUi.toggleFullscreen()"));
    }

    @Test
    public void orientationAwareActionUsesRotationCallbackWithDirectFallback() throws Exception {
        final String source = read("org/schabi/newpipe/player/ui/MainPlayerUi.java");
        final String toggle = methodBody(source, "public void toggleFullscreenWithOrientation()");

        assertTrue(toggle.contains("PlayerServiceEventListener::onScreenRotationButtonClicked"));
        assertTrue(toggle.contains("toggleFullscreen();"));
    }

    @Test
    public void rotationButtonAppliesFullscreenBeforeRequestingOrientation() throws Exception {
        final String source = read(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java");
        final String rotation = methodBody(source,
                "public void onScreenRotationButtonClicked()");

        assertTrue(rotation.contains("ui.setFullscreen(fullscreen);"));
        assertTrue(rotation.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE"));
        assertTrue(rotation.contains("SCREEN_ORIENTATION_PORTRAIT"));
        assertFalse(rotation.contains("DeviceUtils.isLandscape(requireContext())"));
    }

    @Test
    public void explicitFullscreenSetterIsIdempotent() throws Exception {
        final String source = read("org/schabi/newpipe/player/ui/MainPlayerUi.java");
        final String setter = methodBody(source, "public void setFullscreen(");

        assertTrue(setter.contains("if (isFullscreen == fullscreen)"));
        assertTrue(setter.contains("isFullscreen = fullscreen;"));
        assertFalse(setter.contains("isFullscreen = !isFullscreen;"));
    }

    @Test
    public void backExitsFullscreenWithoutPausingPlayback() throws Exception {
        final String source = read(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java");
        final String onBackPressed = methodBody(source, "public boolean onBackPressed()");

        assertTrue(onBackPressed.contains("restoreDefaultOrientation();"));
        assertFalse(onBackPressed.contains("player.pause();"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
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
