package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class EdgeToEdgeIntegrationTest {
    private final Path mainDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void android15ThemeDoesNotOptOutOfEdgeToEdge() throws Exception {
        final String styles = Files.readString(
                mainDirectory.resolve("res/values-v35/styles.xml"));

        assertFalse(styles.contains("windowOptOutEdgeToEdgeEnforcement"));
    }

    @Test
    public void sharedHelperUsesCompatInsetsAndPreservesImeInsets() throws Exception {
        final String source = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/util/EdgeToEdgeHelper.java"));

        assertTrue(source.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"));
        assertTrue(source.contains("WindowInsetsCompat.Type.systemBars()"));
        assertTrue(source.contains("WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(source.contains("new WindowInsetsCompat.Builder(windowInsets)"));
        assertFalse(source.contains("WindowInsetsCompat.CONSUMED"));
    }

    @Test
    public void coreActivitiesApplySystemBarPadding() throws Exception {
        final List<String> activitySources = List.of(
                "java/org/schabi/newpipe/about/AboutActivity.kt",
                "java/org/schabi/newpipe/download/DownloadActivity.java",
                "java/org/schabi/newpipe/error/ErrorActivity.kt",
                "java/org/schabi/newpipe/error/ReCaptchaActivity.java",
                "java/org/schabi/newpipe/player/PlayQueueActivity.java",
                "java/org/schabi/newpipe/settings/SettingsActivity.java");

        for (final String sourcePath : activitySources) {
            final String source = Files.readString(mainDirectory.resolve(sourcePath));
            assertTrue(sourcePath, source.contains("EdgeToEdgeHelper.enable(this)"));
            assertTrue(sourcePath,
                    source.contains("EdgeToEdgeHelper.applySystemBarPadding("));
        }
    }

    @Test
    public void mainActivityDistributesInsetsInsideDrawerLayout() throws Exception {
        final String activity = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/MainActivity.java"));
        final String phoneLayout = Files.readString(mainDirectory.resolve(
                "res/layout/activity_main.xml"));
        final String tabletLayout = Files.readString(mainDirectory.resolve(
                "res/layout-sw600dp/activity_main.xml"));

        assertTrue(activity.contains("EdgeToEdgeHelper.applyDrawerLayoutSystemBarPadding("));
        assertFalse(activity.contains(
                "EdgeToEdgeHelper.applySystemBarPadding(mainBinding.getRoot())"));
        assertTrue(phoneLayout.contains("android:id=\"@+id/main_content\""));
        assertTrue(tabletLayout.contains("android:id=\"@+id/main_content\""));
    }

    @Test
    public void playerDoesNotUseDeprecatedSystemUiVisibilityFlags() throws Exception {
        final String detailSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));
        final String playerSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/ui/MainPlayerUi.java"));
        final String combined = detailSource + playerSource;

        assertFalse(combined.contains("SYSTEM_UI_FLAG_"));
        assertFalse(combined.contains("setSystemUiVisibility"));
        assertTrue(combined.contains("EdgeToEdgeHelper.hideSystemBars"));
        assertTrue(combined.contains("EdgeToEdgeHelper.showSystemBars"));
    }
}
