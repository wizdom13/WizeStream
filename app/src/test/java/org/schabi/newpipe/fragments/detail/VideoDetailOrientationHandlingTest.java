package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VideoDetailOrientationHandlingTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main")
            : Path.of("app/src/main");

    @Test
    public void handledConfigurationChangesResyncPlayerFullscreen() throws Exception {
        final String manifest = Files.readString(projectDirectory.resolve("AndroidManifest.xml"));
        final String fragment = Files.readString(projectDirectory.resolve(
                "java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));

        assertTrue(manifest.contains("android:configChanges="
                + "\"screenSize|smallestScreenSize|screenLayout|orientation\""));
        assertTrue(fragment.contains("public void onConfigurationChanged("));
        assertTrue(fragment.contains("syncFullscreenWithOrientation("));
        assertTrue(fragment.contains("newConfig.orientation"));
        assertTrue(fragment.contains("ui.setFullscreen(fullscreenStateForOrientation("));
        assertTrue(fragment.contains("binding.getRoot().post("));
        assertTrue(fragment.contains("detailLayoutRecreationRequested"));
        assertTrue(fragment.contains("reconcileDetailLayoutAfterConfigurationChange"));
        assertTrue(fragment.contains("restoreDetailLayoutAfterConfigurationChange"));
        assertTrue(fragment.contains("prepareAndHandleInfo(currentInfo, false)"));
        assertTrue(fragment.contains("fullscreen ? View.GONE : View.VISIBLE"));
    }

    @Test
    public void landscapePhoneVideoEntersFullscreenRegardlessOfPlaybackState() {
        assertTrue(VideoDetailFragment.fullscreenStateForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false,
                false));
    }

    @Test
    public void portraitHorizontalVideoExitsFullscreen() {
        assertFalse(VideoDetailFragment.fullscreenStateForOrientation(
                Configuration.ORIENTATION_PORTRAIT,
                true,
                false,
                false,
                false));
    }

    @Test
    public void portraitVerticalVideoKeepsDirectFullscreenState() {
        assertTrue(VideoDetailFragment.fullscreenStateForOrientation(
                Configuration.ORIENTATION_PORTRAIT,
                true,
                true,
                false,
                false));
    }

    @Test
    public void tabletAndAudioOnlyPlaybackIgnoreOrientationChanges() {
        assertFalse(VideoDetailFragment.fullscreenStateForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                true,
                false));
        assertFalse(VideoDetailFragment.fullscreenStateForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false,
                true));
    }
}
