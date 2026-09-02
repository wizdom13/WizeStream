package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PlayerCollapseOrientationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void collapsedPhonePlayerReleasesFullscreenOrientation() throws Exception {
        final String behavior = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/gesture/CustomBottomSheetBehavior.java"));
        final String stateChanged = methodBody(behavior, "public void onStateChanged(");
        final String restoreMethod = methodBody(
                behavior, "private void restorePhoneOrientationAfterFullscreenCollapse(");

        assertTrue(stateChanged.contains("newState == STATE_COLLAPSED"));
        assertTrue(stateChanged.contains("restorePhoneOrientationAfterFullscreenCollapse"));
        assertTrue(restoreMethod.contains("DeviceUtils.isTablet(activity)"));
        assertTrue(restoreMethod.contains("DeviceUtils.isTv(activity)"));
        assertTrue(restoreMethod.contains("DeviceUtils.isDesktopMode(activity)"));
        assertTrue(restoreMethod.contains("exitMainPlayerFullscreenForMiniPlayer()"));
        assertTrue(restoreMethod.contains("SCREEN_ORIENTATION_UNSPECIFIED"));
    }

    @Test
    public void miniPlayerFullscreenExitDoesNotChangePlayback() throws Exception {
        final String holder = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/helper/PlayerHolder.java"));
        final String method = methodBody(
                holder, "public boolean exitMainPlayerFullscreenForMiniPlayer()");

        assertTrue(method.contains("playerUi.isFullscreen()"));
        assertTrue(method.contains("playerUi.setFullscreen(false)"));
        assertFalse(method.contains(".play("));
        assertFalse(method.contains(".pause("));
        assertFalse(method.contains("seekTo("));
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
