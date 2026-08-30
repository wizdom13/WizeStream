package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertTrue;

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
        assertTrue(fragment.contains("binding.getRoot().post("));
        assertTrue(fragment.contains("detailLayoutRecreationRequested"));
        assertTrue(fragment.contains("reconcileDetailLayoutAfterConfigurationChange"));
        assertTrue(fragment.contains("restoreDetailLayoutAfterConfigurationChange"));
        assertTrue(fragment.contains("prepareAndHandleInfo(currentInfo, false)"));
        assertTrue(fragment.contains("fullscreen ? View.GONE : View.VISIBLE"));
    }
}
