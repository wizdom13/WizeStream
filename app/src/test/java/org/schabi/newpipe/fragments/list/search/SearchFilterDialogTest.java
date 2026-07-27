package org.schabi.newpipe.fragments.list.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchFilterDialogTest {
    @Test
    public void keepsOneSelectionPerExclusiveGroupAndMultipleFeatures() {
        final Set<Integer> selected = new LinkedHashSet<>(
                Arrays.asList(2, 12, 31, 32));

        SearchFilterDialog.normalizeSortFilters(videoGroups(), selected, false);

        assertEquals(Arrays.asList(2, 12, 21, 31, 32), new ArrayList<>(selected));
    }

    @Test
    public void removesUnsupportedFiltersAndDefaultsExclusiveGroups() {
        final Set<Integer> selected = new LinkedHashSet<>(Arrays.asList(12, 31));
        final List<FilterGroup> playlistGroups = List.of(
                exclusive("sortby", item(1, "sort_relevance"), item(2, "sort_rating")));

        SearchFilterDialog.normalizeSortFilters(playlistGroups, selected, false);

        assertEquals(List.of(1), new ArrayList<>(selected));
    }

    @Test
    public void preservesSelectionsSupportedByTheNewContentType() {
        final Set<Integer> selected = new LinkedHashSet<>(Arrays.asList(2, 12, 31));
        final List<FilterGroup> playlistGroups = List.of(
                exclusive("sortby", item(1, "sort_relevance"), item(2, "sort_rating")));

        SearchFilterDialog.normalizeSortFilters(playlistGroups, selected, false);

        assertEquals(List.of(2), new ArrayList<>(selected));
    }

    @Test
    public void addsDefaultsWhenNoSortSelectionWasRestored() {
        final Set<Integer> selected = new LinkedHashSet<>();

        SearchFilterDialog.normalizeSortFilters(videoGroups(), selected, false);

        assertEquals(Arrays.asList(1, 11, 21), new ArrayList<>(selected));
    }

    @Test
    public void youtubeMusicModeKeepsOnlyMusicContentTypes() {
        final List<FilterItem> allFilters = Arrays.asList(
                item(1, "all"),
                item(2, "videos"),
                item(3, "music_songs"),
                item(4, "music_videos"),
                item(5, "music_albums"),
                item(6, "music_playlists"),
                item(7, "music_artists"));

        final List<String> names = SearchFilterDialog.filterContentTypes(allFilters, true)
                .stream()
                .map(FilterItem::getName)
                .toList();

        assertEquals(Arrays.asList(
                "music_songs",
                "music_videos",
                "music_albums",
                "music_playlists",
                "music_artists"), names);
    }

    @Test
    public void regularModeKeepsEveryContentType() {
        final List<FilterItem> allFilters = Arrays.asList(
                item(1, "all"), item(2, "music_songs"));

        assertEquals(allFilters, SearchFilterDialog.filterContentTypes(allFilters, false));
    }

    private static List<FilterGroup> videoGroups() {
        return Arrays.asList(
                exclusive("sortby", item(1, "sort_relevance"), item(2, "sort_rating")),
                exclusive("upload_date", item(11, "all"), item(12, "past_week")),
                exclusive("duration", item(21, "all"), item(22, "short_video")),
                multiple("features", item(31, "HD"), item(32, "Subtitles"))
        );
    }

    private static FilterGroup exclusive(final String name, final FilterItem... filters) {
        return group(name, true, filters);
    }

    private static FilterGroup multiple(final String name, final FilterItem... filters) {
        return group(name, false, filters);
    }

    private static FilterGroup group(final String name, final boolean exclusive,
                                     final FilterItem... filters) {
        return new FilterGroup(0, name, exclusive, filters);
    }

    private static FilterItem item(final int id, final String name) {
        return new FilterItem(id, name);
    }
}
