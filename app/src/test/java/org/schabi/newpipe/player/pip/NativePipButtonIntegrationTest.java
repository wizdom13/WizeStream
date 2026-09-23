package org.schabi.newpipe.player.pip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class NativePipButtonIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourceDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void pipEntryAvoidsPreTransitionFullscreenPreparation() throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/player/pip/NativePipController.java");
        final String dedicatedEntry = methodBody(source,
                "public boolean enterPictureInPicture()");
        final String homeEntry = methodBody(source, "public void onUserLeaveHint()");

        assertTrue(dedicatedEntry.contains("activity.enterPictureInPictureMode(params)"));
        assertFalse(dedicatedEntry.contains("prepareNativePipEntry()"));
        assertFalse(homeEntry.contains("prepareNativePipEntry()"));

        final String detailSource = readSource(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java");
        final String confirmedPipCallback = methodBody(
                detailSource, "public void onNativePipModeChanged(");
        final String playerUiSource = readSource(
                "org/schabi/newpipe/player/ui/VideoPlayerUi.java");

        assertTrue(confirmedPipCallback.contains("prepareNativePipEntry()"));
        assertTrue(confirmedPipCallback.contains(
                "restoreVideoSurfaceAfterLayoutTransition"));
        assertTrue(playerUiSource.contains(
                "public final void restoreVideoSurfaceAfterLayoutTransition()"));
        assertTrue(playerUiSource.contains("restoreVideoAspectRatioFromPlayer()"));
        assertTrue(playerUiSource.contains("rebindVideoSurfaceIfValid"));
        assertTrue(playerUiSource.contains("postOnAnimation"));
    }

    @Test
    public void primaryPlayerActionUsesConfiguredFloatingPlayerPreference() throws Exception {
        final String source = readSource("org/schabi/newpipe/views/NewPipeTextView.java");
        final String clickListener = methodBody(source, "public void setOnClickListener(");

        assertTrue(clickListener.contains("configureFloatingPlayerActions()"));
        assertTrue(source.contains("FloatingPlayerActionPreference.primaryAction"));
        assertTrue(source.contains("enterNativePictureInPicture()"));
        assertTrue(source.contains("popupClickListener.onClick(view)"));
        assertTrue(source.contains("R.id.detail_controls_popup"));
        assertTrue(source.contains("R.layout.detail_legacy_popup_action"));
    }

    @Test
    public void alternateFloatingPlayerActionUsesASeparateSecondaryControl() throws Exception {
        final String layout = Files.readString(
                resourceDirectory.resolve("layout/detail_legacy_popup_action.xml"));

        assertTrue(layout.contains("@+id/detail_controls_secondary_floating_player"));
        assertTrue(layout.contains("@string/controls_popup_title"));
        assertTrue(layout.contains("@drawable/ic_smart_display"));
    }

    private String readSource(final String relativePath) throws Exception {
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
