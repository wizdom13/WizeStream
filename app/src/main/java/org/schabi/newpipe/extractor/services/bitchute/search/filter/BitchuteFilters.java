// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.extractor.services.bitchute.search.filter;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.channels.ResultsSearchChannels;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.videos.ResultsSearchVideos;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.SearchFiltersBase;

import java.util.ArrayList;
import java.util.List;

public final class BitchuteFilters extends SearchFiltersBase {
    public static final int ID_CF_MAIN_VIDEOS = 0;
    public static final int ID_CF_MAIN_CHANNELS = 1;

    private int relevanceId;
    private int allDurationsId;
    private int normalSensitivityId;
    private JsonBuilder<JsonObject> currentJsonSearchObject;

    public BitchuteFilters() {
        init();
        build();
    }

    @Override
    protected void init() {
        final int videos = builder.addFilterItem(new BitchuteKindContentFilterItem(
                "videos", "kind", "video", ResultsSearchVideos.ENDPOINT));
        final int channels = builder.addFilterItem(new BitchuteKindContentFilterItem(
                "channels", "kind", "channel", ResultsSearchChannels.ENDPOINT));
        defaultContentFilterId = videos;

        addContentFilter(builder.createSortGroup(null, true, new FilterItem[]{
                builder.getFilterForId(videos), builder.getFilterForId(channels)
        }));

        relevanceId = builder.addSortItem(
                new BitchuteKeyValueFilterItem("sort_relevance", "sort", ""));
        final int newest = builder.addSortItem(
                new BitchuteKeyValueFilterItem("latest", "sort", "new"));
        final int oldest = builder.addSortItem(
                new BitchuteKeyValueFilterItem("oldest", "sort", "old"));
        allDurationsId = builder.addSortItem(
                new BitchuteKeyValueFilterItem("all", "duration", ""));
        final int shortDuration = builder.addSortItem(
                new BitchuteKeyValueFilterItem("short_video", "duration", "short"));
        final int mediumDuration = builder.addSortItem(
                new BitchuteKeyValueFilterItem("medium_length", "duration", "medium"));
        final int longDuration = builder.addSortItem(
                new BitchuteKeyValueFilterItem("long_video", "duration", "long"));
        final int featureDuration = builder.addSortItem(
                new BitchuteKeyValueFilterItem("extra_long", "duration", "feature"));
        final int safeSensitivity = builder.addSortItem(
                new BitchuteKeyValueFilterItem("safe", "sensitivity_id", "safe"));
        normalSensitivityId = builder.addSortItem(
                new BitchuteKeyValueFilterItem("normal", "sensitivity_id", "normal"));
        final int nsfwSensitivity = builder.addSortItem(
                new BitchuteKeyValueFilterItem("nsfw", "sensitivity_id", "nsfw"));
        final int nsflSensitivity = builder.addSortItem(
                new BitchuteKeyValueFilterItem("nsfl", "sensitivity_id", "nsfl"));

        final Filter sortFilters = new Filter.Builder(new FilterGroup[]{
                builder.createSortGroup("sortby", true, new FilterItem[]{
                        builder.getFilterForId(relevanceId), builder.getFilterForId(newest),
                        builder.getFilterForId(oldest)
                }),
                builder.createSortGroup("duration", true, new FilterItem[]{
                        builder.getFilterForId(allDurationsId),
                        builder.getFilterForId(shortDuration),
                        builder.getFilterForId(mediumDuration),
                        builder.getFilterForId(longDuration),
                        builder.getFilterForId(featureDuration)
                }),
                builder.createSortGroup("sensitivity", true, new FilterItem[]{
                        builder.getFilterForId(safeSensitivity),
                        builder.getFilterForId(normalSensitivityId),
                        builder.getFilterForId(nsfwSensitivity),
                        builder.getFilterForId(nsflSensitivity)
                })
        }).build();
        addContentFilterSortVariant(Filter.ITEM_IDENTIFIER_UNKNOWN, sortFilters);
        addContentFilterSortVariant(videos, sortFilters);
    }

    @Override
    public String evaluateSelectedFilters(final String ignoredSearchString) {
        if (selectedContentFilter == null || selectedContentFilter.isEmpty()) {
            selectedContentFilter = List.of(builder.getFilterForId(defaultContentFilterId));
            final List<FilterItem> defaults = new ArrayList<>();
            defaults.add(builder.getFilterForId(relevanceId));
            defaults.add(builder.getFilterForId(allDurationsId));
            defaults.add(builder.getFilterForId(normalSensitivityId));
            selectedSortFilter = defaults;
        }

        currentJsonSearchObject = JsonObject.builder();
        final String query = evaluateSelectedContentFilters() + evaluateSelectedSortFilters();
        final BitchuteKindContentFilterItem contentItem = getFirstContentFilterItem();
        if (contentItem != null) {
            contentItem.setDataParams(query);
            contentItem.setDataParamsJson(currentJsonSearchObject);
        }
        return query;
    }

    @Override
    public String evaluateSelectedSortFilters() {
        final StringBuilder query = new StringBuilder();
        if (selectedSortFilter != null) {
            for (final FilterItem item : selectedSortFilter) {
                final BitchuteKeyValueFilterItem filter = (BitchuteKeyValueFilterItem) item;
                if (!filter.query.isEmpty()) {
                    query.append('&').append(filter.key).append('=').append(filter.query);
                    currentJsonSearchObject.value(filter.key, filter.query);
                }
            }
        }
        return query.toString();
    }

    @Override
    public String evaluateSelectedContentFilters() {
        final BitchuteKindContentFilterItem item = getFirstContentFilterItem();
        if (item == null) {
            return "";
        }
        currentJsonSearchObject.value(item.key, item.query);
        return "&" + item.key + "=" + item.query;
    }

    public BitchuteKindContentFilterItem getFirstContentFilterItem() {
        if (selectedContentFilter == null || selectedContentFilter.isEmpty()) {
            return null;
        }
        return (BitchuteKindContentFilterItem) selectedContentFilter.get(0);
    }

    public static class BitchuteKindContentFilterItem extends BitchuteKeyValueFilterItem {
        public final String endpoint;

        BitchuteKindContentFilterItem(final String name, final String key, final String query,
                                      final String endpoint) {
            super(name, key, query);
            this.endpoint = endpoint;
        }
    }

    public static class BitchuteKeyValueFilterItem extends FilterItem {
        final String key;
        final String query;
        private String dataParams = "";
        private JsonBuilder<JsonObject> dataParamsJson;

        BitchuteKeyValueFilterItem(final String name, final String key, final String query) {
            super(Filter.ITEM_IDENTIFIER_UNKNOWN, name);
            this.key = key;
            this.query = query;
        }

        public String getDataParams() {
            return dataParams;
        }

        public JsonBuilder<JsonObject> getDataParamsNew() {
            return dataParamsJson;
        }

        void setDataParams(final String dataParams) {
            this.dataParams = dataParams;
        }

        void setDataParamsJson(final JsonBuilder<JsonObject> dataParamsJson) {
            this.dataParamsJson = dataParamsJson;
        }
    }
}
