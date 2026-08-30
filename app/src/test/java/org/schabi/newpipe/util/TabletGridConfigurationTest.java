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
    public void appearanceSettingsExposeAutomaticAndFixedTabletColumns() throws Exception {
        final String appearanceSettings = read(
                "app/src/main/res/xml/appearance_settings.xml");
        final String appearanceFragment = read(
                "app/src/main/java/org/schabi/newpipe/settings/"
                        + "AppearanceSettingsFragment.java");

        assertTrue(appearanceSettings.contains(
                "android:defaultValue=\"@string/grid_columns_auto_key\""));
        assertTrue(appearanceSettings.contains(
                "android:entries=\"@array/grid_columns_description\""));
        assertTrue(appearanceSettings.contains(
                "android:entryValues=\"@array/grid_columns_values\""));
        assertTrue(appearanceFragment.contains(
                "setPreferenceVisible(R.string.grid_columns_key, "
                        + "showTabletNavigationPreferences);"));
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
    public void thumbnailCardsFillTheirColumnAtSixteenByNine() throws Exception {
        for (final String layout : List.of(
                "app/src/main/res/layout/list_stream_grid_item.xml",
                "app/src/main/res/layout/list_stream_playlist_grid_item.xml",
                "app/src/main/res/layout/list_playlist_grid_item.xml")) {
            final String source = read(layout);

            assertTrue(source.contains(
                    "android:id=\"@+id/itemThumbnailView\"\n"
                            + "        android:layout_width=\"0dp\"\n"
                            + "        android:layout_height=\"0dp\""));
            assertTrue(source.contains(
                    "app:layout_constraintDimensionRatio=\"H,16:9\""));
        }
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
