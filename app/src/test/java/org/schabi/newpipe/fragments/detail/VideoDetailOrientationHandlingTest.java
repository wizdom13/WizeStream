package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VideoDetailOrientationHandlingTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void configurationOwnsFullscreenWithoutDeferredOrientationState()
            throws Exception {
        final String fragment = readFragment();
        assertTrue(fragment.contains("final int orientation = newConfig.orientation;"));
        assertTrue(fragment.contains("ui.setFullscreen(true)"));
        assertTrue(fragment.contains("ui.setFullscreen(false)"));
        assertFalse(fragment.contains("pendingFullscreenOrientation"));
        assertFalse(fragment.contains("fullscreenStateForOrientation("));
    }

    @Test
    public void expandedPhoneVideoKeepsCurrentLayoutForLandscapeFullscreen() {
        assertTrue(VideoDetailFragment.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false,
                BottomSheetBehavior.STATE_EXPANDED, false));
        assertFalse(VideoDetailFragment.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_PORTRAIT,
                true, true, false,
                BottomSheetBehavior.STATE_EXPANDED, false));
        assertFalse(VideoDetailFragment.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, true,
                BottomSheetBehavior.STATE_EXPANDED, false));
        assertFalse(VideoDetailFragment.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false,
                BottomSheetBehavior.STATE_COLLAPSED, false));
        assertFalse(VideoDetailFragment.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false,
                BottomSheetBehavior.STATE_EXPANDED, true));
    }

    @Test
    public void fullscreenLayoutGuardRunsBeforeWideLayoutRecreation()
            throws Exception {
        final String configuration = methodBody(
                readFragment(), "public void onConfigurationChanged(");
        final int keepLayout = configuration.indexOf(
                "shouldKeepPhonePlayerLayoutForLandscape(");
        final int recreate = configuration.indexOf("shouldRecreateDetailLayout(");
        final int postedSync = configuration.indexOf("binding.getRoot().post(");
        assertTrue(keepLayout >= 0);
        assertTrue(recreate > keepLayout);
        assertTrue(postedSync > recreate);
        assertTrue(configuration.contains(
                "getResources().getConfiguration().orientation"));
    }

    @Test
    public void expandedPlayerUsesConfigurationOrientation() throws Exception {
        final String fragment = readFragment();
        final String marker =
                "// Conditions when the player should be expanded to fullscreen";
        final int markerIndex = fragment.indexOf(marker);
        assertTrue(markerIndex >= 0);

        final int blockEnd = Math.min(fragment.length(), markerIndex + 700);
        final String expandedPlayerBlock = fragment.substring(markerIndex, blockEnd);
        assertTrue(expandedPlayerBlock.contains(
                "getResources().getConfiguration().orientation"));
        assertTrue(expandedPlayerBlock.contains(
                "Configuration.ORIENTATION_LANDSCAPE"));
        assertFalse(expandedPlayerBlock.contains(
                "DeviceUtils.isLandscape(requireContext())"));
    }

    @Test
    public void detailLayoutRestorationStillRebindsPlayer() throws Exception {
        final String restore = methodBody(
                readFragment(),
                "private void restoreDetailLayoutAfterConfigurationChange()");
        assertTrue(restore.contains("tryAddVideoPlayerView();"));
        assertTrue(restore.contains("updatePinnedPlayerLayout();"));
        assertTrue(restore.contains("syncFullscreenWithOrientation("));
    }

    private String readFragment() throws Exception {
        return Files.readString(projectDirectory.resolve(
                "java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));
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
