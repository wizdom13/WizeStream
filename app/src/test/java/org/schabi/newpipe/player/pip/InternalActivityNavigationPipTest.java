package org.schabi.newpipe.player.pip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class InternalActivityNavigationPipTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void settingsAndAboutSuppressPipBeforeOpening() throws Exception {
        final String mainActivity = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/MainActivity.java"));
        final String options = methodBody(mainActivity,
                "private void optionsAboutSelected(final MenuItem item)");

        assertGuardBeforeOpen(options, "case ITEM_ID_SETTINGS:",
                "NavigationHelper.openSettings(this);");
        assertGuardBeforeOpen(options, "case ITEM_ID_ABOUT:",
                "NavigationHelper.openAbout(this);");
    }

    @Test
    public void controllerDefersPipPreparationUntilTransitionIsConfirmed()
            throws Exception {
        final String controller = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/pip/NativePipController.java"));
        final String leaveHint = methodBody(controller, "public void onUserLeaveHint()");
        final int internalGuard = leaveHint.indexOf("internalActivityNavigationPending");
        final int pipRequest = leaveHint.indexOf("activity.enterPictureInPictureMode(params)");

        assertTrue(internalGuard >= 0);
        assertTrue(pipRequest > internalGuard);
        assertFalse(leaveHint.contains("fragment.prepareNativePipEntry()"));
        assertTrue(controller.contains(
                "currentFragment().orElse(null), !internalActivityNavigationPending"));

        final String detailFragment = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));
        final String pipModeChanged = methodBody(detailFragment,
                "public void onNativePipModeChanged(final boolean inPictureInPictureMode)");
        assertTrue(pipModeChanged.contains("prepareNativePipEntry();"));
    }

    @Test
    public void returningToMainActivityRestoresNormalPipParams() throws Exception {
        final String mainActivity = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/MainActivity.java"));
        final String onStart = methodBody(mainActivity, "protected void onStart()");
        assertTrue(onStart.contains("nativePipController.onMainActivityStarted();"));
    }

    private static void assertGuardBeforeOpen(final String source,
                                              final String caseMarker,
                                              final String openCall) {
        final int caseStart = source.indexOf(caseMarker);
        final int guard = source.indexOf(
                "nativePipController.prepareForInternalActivityNavigation();", caseStart);
        final int open = source.indexOf(openCall, caseStart);
        assertTrue(caseStart >= 0);
        assertTrue(guard > caseStart);
        assertTrue(open > guard);
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
