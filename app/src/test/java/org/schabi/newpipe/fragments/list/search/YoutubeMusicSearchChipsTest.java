/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class YoutubeMusicSearchChipsTest {
    private final Path repositoryRoot =
            Files.exists(Path.of("app/src/main/res/layout/toolbar_layout.xml"))
                    ? Path.of(".") : Path.of("..");

    @Test
    public void chipsAreLimitedToYoutubeMusicWithAvailableFilters() {
        assertTrue(SearchFragment.shouldShowMusicFilterChips(true, true, true));
        assertFalse(SearchFragment.shouldShowMusicFilterChips(false, true, true));
        assertFalse(SearchFragment.shouldShowMusicFilterChips(true, false, true));
        assertFalse(SearchFragment.shouldShowMusicFilterChips(true, true, false));
    }

    @Test
    public void restoredMusicFilterRemainsSelected() {
        assertEquals("music_videos", SearchFragment.resolveMusicFilterName(
                musicFilters(), new String[]{"music_videos"}));
    }

    @Test
    public void missingOrInvalidSelectionFallsBackToSongs() {
        assertEquals("music_songs", SearchFragment.resolveMusicFilterName(
                musicFilters(), new String[0]));
        assertEquals("music_songs", SearchFragment.resolveMusicFilterName(
                musicFilters(), new String[]{"videos"}));
    }

    @Test
    public void toolbarHostsSingleSelectionScrollableFilterChips() throws Exception {
        final String toolbar = read("app/src/main/res/layout/toolbar_layout.xml");
        final String chipGroup = read(
                "app/src/main/res/layout/toolbar_search_music_filter_chips.xml");
        final String chip = read(
                "app/src/main/res/layout/item_search_music_filter_chip.xml");

        assertTrue(toolbar.contains("@layout/toolbar_search_music_filter_chips"));
        assertTrue(chipGroup.contains("<HorizontalScrollView"));
        assertTrue(chipGroup.contains("app:singleSelection=\"true\""));
        assertTrue(chipGroup.contains("app:selectionRequired=\"true\""));
        assertTrue(chip.contains("@style/Widget.Material3.Chip.Filter"));
    }

    @Test
    public void chipsReuseExtractorFiltersAndApplySelectionsImmediately() throws Exception {
        final String searchFragment = read(
                "app/src/main/java/org/schabi/newpipe/fragments/list/search/SearchFragment.java");

        assertTrue(searchFragment.contains(
                "SearchFilterDialog.getContentFilters(service, true)"));
        assertTrue(searchFragment.contains(
                "applySearchFilters((String) selectedChip.getTag(), Collections.emptyList())"));
        assertTrue(searchFragment.contains("this::applySearchFilters"));
        assertTrue(searchFragment.contains(
                "searchFilter.setVisibility(hasFilters && !showMusicFilterChips"));
    }

    private static List<FilterItem> musicFilters() {
        return List.of(
                new FilterItem(1, "music_songs"),
                new FilterItem(2, "music_videos"),
                new FilterItem(3, "music_albums"),
                new FilterItem(4, "music_playlists"),
                new FilterItem(5, "music_artists"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
