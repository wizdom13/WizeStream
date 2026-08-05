package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PopupPlayerReturnStateTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void consumesRememberedPortraitStateInsteadOfFullscreenFallback() {
        final PopupPlayerReturnState state = new PopupPlayerReturnState();
        state.remember(false);

        assertFalse(state.consume(true));
    }

    @Test
    public void consumesRememberedFullscreenStateInsteadOfPortraitFallback() {
        final PopupPlayerReturnState state = new PopupPlayerReturnState();
        state.remember(true);

        assertTrue(state.consume(false));
    }

    @Test
    public void rememberedStateIsConsumedOnlyOnce() {
        final PopupPlayerReturnState state = new PopupPlayerReturnState();
        state.remember(true);

        assertTrue(state.consume(false));
        assertFalse(state.consume(false));
    }

    @Test
    public void usesPreferenceFallbackWithoutRememberedState() {
        final PopupPlayerReturnState state = new PopupPlayerReturnState();

        assertTrue(state.consume(true));
        assertFalse(state.consume(false));
    }

    @Test
    public void popupReturnNavigationDoesNotForceFullscreen() throws Exception {
        final String navigationHelper = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/util/NavigationHelper.java"));

        assertTrue(navigationHelper.contains("consumeMainPlayerFullscreenBeforePopup"));
        assertFalse(navigationHelper.contains("playerType == PlayerType.POPUP\n"
                + "                        || PlayerHelper"
                + ".isStartMainPlayerFullscreenEnabled(context)"));
    }

    @Test
    public void popupButtonRemembersStateBeforeCollapsingFullscreen() throws Exception {
        final String detailFragment = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));
        final String openPopupPlayer = methodBody(detailFragment,
                "private void openPopupPlayer(final boolean append)");
        final int rememberState = openPopupPlayer.indexOf(
                "rememberMainPlayerFullscreenBeforePopup");
        final int collapseFullscreen = openPopupPlayer.indexOf(
                "toggleFullscreenIfInFullscreenMode");

        assertTrue(rememberState >= 0);
        assertTrue(collapseFullscreen >= 0);
        assertTrue(rememberState < collapseFullscreen);
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
