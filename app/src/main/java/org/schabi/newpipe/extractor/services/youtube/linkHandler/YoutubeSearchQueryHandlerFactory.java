package org.schabi.newpipe.extractor.services.youtube.linkHandler;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;

import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

public final class YoutubeSearchQueryHandlerFactory extends SearchQueryHandlerFactory {

    public static final String ALL = "all";
    public static final String VIDEOS = "videos";
    public static final String CHANNELS = "channels";
    public static final String PLAYLISTS = "playlists";

    public static final String MUSIC_SONGS = "music_songs";
    public static final String MUSIC_VIDEOS = "music_videos";
    public static final String MUSIC_ALBUMS = "music_albums";
    public static final String MUSIC_PLAYLISTS = "music_playlists";
    public static final String MUSIC_ARTISTS = "music_artists";

    private final YoutubeFilters searchFilters = new YoutubeFilters();

    private static YoutubeSearchQueryHandlerFactory instance = null;

    /**
     * Singleton to get the same objects of filters during search.
     *
     * The content filter holds a variable search parameter: (filter.getParams())
     *
     * @return
     */
    @Nonnull
    public static synchronized YoutubeSearchQueryHandlerFactory getInstance() {
        if (instance == null) {
            instance = new YoutubeSearchQueryHandlerFactory();
        }
        return instance;
    }

    @Override
    public String getUrl(final String searchString,
                         @Nonnull final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        searchFilters.setSelectedContentFilter(resolveSelectedContentFilter(selectedContentFilter));
        searchFilters.setSelectedSortFilter(selectedSortFilter);
        return searchFilters.evaluateSelectedFilters(searchString);
    }

    @Nonnull
    private List<FilterItem> resolveSelectedContentFilter(
            final List<FilterItem> selectedContentFilter) {
        if (selectedContentFilter != null && !selectedContentFilter.isEmpty()) {
            return selectedContentFilter;
        }

        final Filter availableContentFilter = searchFilters.getContentFilters();
        if (availableContentFilter == null) {
            return Collections.emptyList();
        }

        for (final FilterGroup group : availableContentFilter.getFilterGroups()) {
            if (group.filterItems != null && group.filterItems.length > 0) {
                return Collections.singletonList(group.filterItems[0]);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Filter getAvailableContentFilter() {
        return searchFilters.getContentFilters();
    }

    @Override
    public Filter getAvailableSortFilter() {
        return searchFilters.getSortFilters();
    }

    @Override
    public Filter getContentFilterSortFilterVariant(final int contentFilterId) {
        return searchFilters.getContentFilterSortFilterVariant(contentFilterId);
    }

    @Override
    public FilterItem getFilterItem(final int filterId) {
        return searchFilters.getFilterItem(filterId);
    }
}