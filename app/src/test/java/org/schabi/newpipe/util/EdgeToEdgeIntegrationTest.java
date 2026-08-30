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

        assertTrue(activity.contains("EdgeToEdgeHelper.applyMainActivitySystemBarInsets("));
        assertTrue(activity.contains("mainBinding.mainNavigationRail"));
        assertTrue(activity.contains("mainBinding.mainBottomSystemBarScrim"));
        assertFalse(activity.contains(
                "EdgeToEdgeHelper.applySystemBarPadding(mainBinding.getRoot())"));
        assertTrue(phoneLayout.contains("android:id=\"@+id/main_content\""));
        assertTrue(tabletLayout.contains("android:id=\"@+id/main_content\""));
        assertTrue(phoneLayout.contains("android:id=\"@+id/main_safe_content\""));
        assertTrue(tabletLayout.contains("android:id=\"@+id/main_safe_content\""));
        assertTrue(phoneLayout.contains(
                "android:id=\"@+id/main_bottom_system_bar_scrim\""));
        assertTrue(tabletLayout.contains(
                "android:id=\"@+id/main_bottom_system_bar_scrim\""));
        assertTrue(phoneLayout.contains("android:id=\"@+id/main_navigation_rail\""));
        assertTrue(tabletLayout.contains("android:id=\"@+id/main_navigation_rail\""));
    }

    @Test
    public void playerSheetStaysOutsideInsetContentContainer() throws Exception {
        final List<String> layouts = List.of(
                "res/layout/activity_main.xml",
                "res/layout-sw600dp/activity_main.xml");

        for (final String layoutPath : layouts) {
            final String layout = Files.readString(mainDirectory.resolve(layoutPath));
            final int safeContentStart = layout.indexOf(
                    "android:id=\"@+id/main_safe_content\"");
            final int safeContentEnd = layout.indexOf("</FrameLayout>", safeContentStart);
            final int playerSheetStart = layout.indexOf(
                    "android:id=\"@+id/fragment_player_holder\"");

            assertTrue(layoutPath, safeContentStart >= 0);
            assertTrue(layoutPath, safeContentEnd > safeContentStart);
            assertTrue(layoutPath, playerSheetStart > safeContentEnd);
        }
    }

    @Test
    public void playerSheetRemainsEdgeToEdgeWhilePeekHeightKeepsBottomInset() throws Exception {
        final String insetHelper = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/util/EdgeToEdgeHelper.java"));
        final String behavior = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/gesture/CustomBottomSheetBehavior.java"));

        assertTrue(insetHelper.contains(
                "updatePlayerSheetBottomInset(playerSheet, safeInsets.bottom)"));
        assertFalse(insetHelper.contains("playerLeft + safeInsets.left"));
        assertFalse(insetHelper.contains("playerRight + safeInsets.right"));
        assertFalse(insetHelper.contains("playerBottom + safeInsets.bottom"));
        assertTrue(behavior.contains("bottomSystemBarInset"));
        assertTrue(behavior.contains("playerPeekHeight, navigationHeight,"
                + " bottomNavigationVisible,"));
    }

    @Test
    public void expandedPlayerIsDrawnAboveTheParentToolbar() throws Exception {
        final List<String> layouts = List.of(
                "res/layout/activity_main.xml",
                "res/layout-sw600dp/activity_main.xml");

        for (final String layoutPath : layouts) {
            final String layout = Files.readString(mainDirectory.resolve(layoutPath));
            final int toolbarStart = layout.indexOf(
                    "android:id=\"@+id/toolbar_layout\"");
            final int playerSheetStart = layout.indexOf(
                    "android:id=\"@+id/fragment_player_holder\"");

            assertTrue(layoutPath, toolbarStart >= 0);
            assertTrue(layoutPath, playerSheetStart > toolbarStart);
        }
    }

    @Test
    public void bottomSystemBarScrimCoversTheCollapsedPlayerSheet() throws Exception {
        final List<String> layouts = List.of(
                "res/layout/activity_main.xml",
                "res/layout-sw600dp/activity_main.xml");

        for (final String layoutPath : layouts) {
            final String layout = Files.readString(mainDirectory.resolve(layoutPath));
            final int playerSheetStart = layout.indexOf(
                    "android:id=\"@+id/fragment_player_holder\"");
            final int scrimStart = layout.indexOf(
                    "android:id=\"@+id/main_bottom_system_bar_scrim\"");
            final int navigationStart = layout.indexOf(
                    "android:id=\"@+id/main_bottom_navigation\"");
            final int navigationRailStart = layout.indexOf(
                    "android:id=\"@+id/main_navigation_rail\"");

            assertTrue(layoutPath, playerSheetStart >= 0);
            assertTrue(layoutPath, scrimStart > playerSheetStart);
            assertTrue(layoutPath, navigationStart > scrimStart);
            assertTrue(layoutPath, navigationRailStart > scrimStart);
            assertTrue(layoutPath,
                    layout.contains("android:background=\"?attr/colorSurfaceContainer\""));
        }
    }

    @Test
    public void miniPlayerOverlayStaysOutsideToolbarInsetDetailContent() throws Exception {
        final String detailLayout = Files.readString(mainDirectory.resolve(
                "res/layout/fragment_video_detail.xml"));
        final int detailContentStart = detailLayout.indexOf(
                "android:id=\"@+id/detail_main_content\"");
        final int detailContentEnd = detailLayout.indexOf(
                "</androidx.coordinatorlayout.widget.CoordinatorLayout>", detailContentStart);
        final int miniPlayerOverlayStart = detailLayout.indexOf(
                "android:id=\"@+id/overlay_layout\"");

        assertTrue(detailContentStart >= 0);
        assertTrue(detailContentEnd > detailContentStart);
        assertTrue(miniPlayerOverlayStart > detailContentEnd);
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

    @Test
    public void fullscreenControlsAvoidInsetsWithoutShrinkingVideoSurface() throws Exception {
        final String playerSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/ui/VideoPlayerUi.java"));
        final String mainPlayerSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/ui/MainPlayerUi.java"));

        assertTrue(playerSource.contains(
                "ViewCompat.getRootWindowInsets(binding.getRoot())"));
        assertTrue(playerSource.contains("WindowInsetsCompat.Type.systemBars()"));
        assertTrue(playerSource.contains("WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(playerSource.contains(
                "binding.playbackControlRoot.setPadding(0, 0, 0, 0)"));
        assertTrue(playerSource.contains("binding.topControls.setPadding("));
        assertTrue(playerSource.contains("binding.bottomControls.setPadding("));
        assertTrue(mainPlayerSource.contains(
                "binding.getRoot().post(this::updateFullscreenOverlayInsets)"));
    }

    @Test
    public void phoneDetailNavigationReservesItsBottomSystemInset() throws Exception {
        final String detailSource = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));

        assertTrue(detailSource.contains("WindowInsetsCompat.Type.navigationBars()"));
        assertTrue(detailSource.contains("WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(detailSource.contains(
                "detailNavigationBaseBottomMargin + bottomInset"));
        assertTrue(detailSource.contains("viewPagerBaseBottomMargin + bottomInset"));
        assertTrue(detailSource.contains("detailNavigation instanceof NavigationRailView"));
    }
}
