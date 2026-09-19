/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TabletGridConfigurationTest {
    private final Path repositoryRoot =
            Files.exists(Path.of("app/src/main/res/xml/appearance_settings.xml"))
                    ? Path.of(".") : Path.of("..");

    @Test
    public void appearanceSettingsExposeAutomaticAndFixedLargeScreenColumns() throws Exception {
        final String appearanceSettings = read(
                "app/src/main/res/xml/appearance_settings.xml");
        final String appearanceFragment = read(
                "app/src/main/java/org/schabi/newpipe/settings/"
                        + "AppearanceSettingsFragment.java");
        final String settingsKeys = read(
                "app/src/main/res/values/settings_keys.xml");

        assertTrue(appearanceSettings.contains(
                "android:defaultValue=\"@string/grid_columns_auto_key\""));
        assertTrue(appearanceSettings.contains(
                "android:entries=\"@array/grid_columns_description\""));
        assertTrue(appearanceSettings.contains(
                "android:entryValues=\"@array/grid_columns_values\""));
        assertTrue(settingsKeys.contains("<string name=\"grid_columns_five_key\">5</string>"));
        assertTrue(settingsKeys.contains("<string name=\"grid_columns_six_key\">6</string>"));
        assertTrue(settingsKeys.contains("<string name=\"grid_columns_seven_key\">7</string>"));
        assertTrue(appearanceFragment.contains(
                "DeviceUtils.isTablet(requireContext())\n"
                        + "                || DeviceUtils.isTv(requireContext())"));
        assertTrue(appearanceFragment.contains(
                "setPreferenceVisible(R.string.grid_columns_key, "
                        + "showLargeScreenPreferences);"));
    }

    @Test
    public void compactLargeScreenChromeReclaimsToolbarAndRailSpace() throws Exception {
        final String appearanceSettings = read(
                "app/src/main/res/xml/appearance_settings.xml");
        final String activity = read(
                "app/src/main/java/org/schabi/newpipe/MainActivity.java");
        final String mainFragment = read(
                "app/src/main/java/org/schabi/newpipe/fragments/MainFragment.java");
        final String dimensions = read(
                "app/src/main/res/values/dimens.xml");

        assertTrue(appearanceSettings.contains(
                "android:key=\"@string/compact_large_screen_navigation_key\""));
        assertTrue(dimensions.contains(
                "<dimen name=\"main_compact_navigation_rail_width\">64dp</dimen>"));
        assertTrue(dimensions.contains(
                "<dimen name=\"main_compact_toolbar_height\">48dp</dimen>"));
        assertTrue(activity.contains("isCompactLargeScreenNavigationEnabled()"));
        assertTrue(activity.contains("getMainNavigationRailWidth()"));
        assertTrue(mainFragment.contains(
                "isCompactLargeScreenNavigationEnabled()"));
        assertTrue(mainFragment.contains(
                "NavigationBarView.LABEL_VISIBILITY_UNLABELED"));
    }

    @Test
    public void remoteAndLocalGridsUseAndObserveTheColumnPreference() throws Exception {
        for (final String listFragment : List.of(
                "app/src/main/java/org/schabi/newpipe/fragments/list/"
                        + "BaseListFragment.java",
                "app/src/main/java/org/schabi/newpipe/local/"
                        + "BaseLocalListFragment.java")) {
            final String source = read(listFragment);

            assertTrue(source.contains(
                    "GridLayoutManagerHelper.getPreferredSpanCount(activity)"));
            assertTrue(source.contains(
                    "getString(R.string.grid_columns_key).equals(key)"));
        }
    }

    @Test
    public void channelGroupFeedsUseAndObserveTheColumnPreference() throws Exception {
        final String feedFragment = read(
                "app/src/main/java/org/schabi/newpipe/local/feed/FeedFragment.kt");

        assertTrue(feedFragment.contains(
                "GridLayoutManagerHelper.getPreferredSpanCount(requireContext())"));
        assertTrue(feedFragment.contains(
                "getString(R.string.grid_columns_key).equals(key)"));
    }

    @Test
    public void thumbnailCardsFillTheirColumnAtSixteenByNine() throws Exception {
        for (final String layout : List.of(
                "app/src/main/res/layout/list_stream_grid_item.xml",
                "app/src/main/res/layout/list_stream_playlist_grid_item.xml")) {
            final String source = read(layout);

            assertTrue(source.contains(
                    "android:id=\"@+id/itemThumbnailContainer\"\n"
                            + "        android:layout_width=\"0dp\"\n"
                            + "        android:layout_height=\"0dp\""));
            assertTrue(source.contains(
                    "app:layout_constraintDimensionRatio=\"H,16:9\""));
            assertTrue(source.contains(
                    "android:id=\"@+id/itemThumbnailView\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"match_parent\""));
        }
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
