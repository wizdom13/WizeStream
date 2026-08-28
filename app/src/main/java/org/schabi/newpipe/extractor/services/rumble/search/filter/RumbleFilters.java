// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.extractor.services.rumble.search.filter;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;

public final class RumbleFilters extends SearchFiltersBase {
    public static final int ID_CF_MAIN_VIDEOS = 0;
    public static final int ID_CF_MAIN_CHANNELS = 1;

    private static final String SEARCH_VIDEOS_URL = "https://rumble.com/search/video?q=";
    private static final String SEARCH_CHANNEL_URL = "https://rumble.com/search/channel?q=";

    public RumbleFilters() {
        init();
        build();
    }

    @Override
    protected void init() {
        final int videos = builder.addFilterItem(
                new RumbleContentFilterItem("videos", SEARCH_VIDEOS_URL));
        final int channels = builder.addFilterItem(
                new RumbleContentFilterItem("channels", SEARCH_CHANNEL_URL));
        defaultContentFilterId = videos;

        addContentFilter(builder.createSortGroup(null, true, new FilterItem[]{
                builder.getFilterForId(videos),
                builder.getFilterForId(channels)
        }));

        final int relevance = builder.addSortItem(
                new RumbleSortFilterItem("sort_relevance", ""));
        final int mostRecent = builder.addSortItem(
                new RumbleSortFilterItem("sort_publish_time", "sort=date"));
        final int rumbles = builder.addSortItem(
                new RumbleSortFilterItem("sort_likes", "sort=rumbles"));
        final int views = builder.addSortItem(
                new RumbleSortFilterItem("sort_view", "sort=views"));
        final int allDates = builder.addSortItem(
                new RumbleSortFilterItem("all", ""));
        final int today = builder.addSortItem(
                new RumbleSortFilterItem("past_day", "date=today"));
        final int week = builder.addSortItem(
                new RumbleSortFilterItem("past_week", "date=this-week"));
        final int month = builder.addSortItem(
                new RumbleSortFilterItem("past_month", "date=this-month"));
        final int year = builder.addSortItem(
                new RumbleSortFilterItem("past_year", "date=this-year"));
        final int allDurations = builder.addSortItem(
                new RumbleSortFilterItem("all", ""));
        final int longDuration = builder.addSortItem(
                new RumbleSortFilterItem("long_video", "duration=long"));
        final int shortDuration = builder.addSortItem(
                new RumbleSortFilterItem("short_video", "duration=short"));

        final Filter sortFilters = new Filter.Builder(new FilterGroup[]{
                builder.createSortGroup("sortby", true, new FilterItem[]{
                        builder.getFilterForId(relevance),
                        builder.getFilterForId(mostRecent),
                        builder.getFilterForId(rumbles),
                        builder.getFilterForId(views)
                }),
                builder.createSortGroup("upload_date", true, new FilterItem[]{
                        builder.getFilterForId(allDates),
                        builder.getFilterForId(today),
                        builder.getFilterForId(week),
                        builder.getFilterForId(month),
                        builder.getFilterForId(year)
                }),
                builder.createSortGroup("duration", true, new FilterItem[]{
                        builder.getFilterForId(allDurations),
                        builder.getFilterForId(longDuration),
                        builder.getFilterForId(shortDuration)
                })
        }).build();
        addContentFilterSortVariant(Filter.ITEM_IDENTIFIER_UNKNOWN, sortFilters);
        addContentFilterSortVariant(videos, sortFilters);
    }

    @Override
    public String evaluateSelectedSortFilters() {
        final StringBuilder query = new StringBuilder();
        if (selectedSortFilter != null) {
            for (final FilterItem item : selectedSortFilter) {
                final String value = ((RumbleSortFilterItem) item).query;
                if (!value.isEmpty()) {
                    query.append('&').append(value);
                }
            }
        }
        return query.toString();
    }

    @Override
    public String evaluateSelectedContentFilters() {
        if (selectedContentFilter == null || selectedContentFilter.isEmpty()) {
            return SEARCH_VIDEOS_URL;
        }
        return ((RumbleContentFilterItem) selectedContentFilter.get(0)).urlEndpoint;
    }

    private static class RumbleSortFilterItem extends FilterItem {
        private final String query;

        RumbleSortFilterItem(final String name, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.query = query;
        }
    }

    public static class RumbleContentFilterItem extends FilterItem {
        private final String urlEndpoint;

        RumbleContentFilterItem(final String name, final String urlEndpoint) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.urlEndpoint = urlEndpoint;
        }
    }
}
