package org.schabi.newpipe.extractor.utils;

import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public final class BraveNewPipeExtractorUtils {
    private BraveNewPipeExtractorUtils() {
    }

    @Nonnull
    public static <T> List<ListLinkHandler> generateTabsFromSuffixMap(
            final String baseUrl,
            final String id,
            final Map<FilterItem, String> tab2Suffix,
            final T channelData) {

        final List<ListLinkHandler> tabs = new ArrayList<>();

        for (final Map.Entry<FilterItem, String> tab : tab2Suffix.entrySet()) {
            final String url = baseUrl + tab.getValue();
            tabs.add(new CustomTabListLinkHandler<T>(url, url, id, List.of(tab.getKey()), List.of(),
                    channelData));
        }
        return tabs;
    }

    public static class CustomTabListLinkHandler<T> extends ListLinkHandler {

        public final T channelData;

        CustomTabListLinkHandler(
                final String originalUrl,
                final String url,
                final String id,
                final List<FilterItem> selectedContentFilters,
                final List<FilterItem> selectedSortFilter,
                final T channelData) {
            super(originalUrl, url, id, selectedContentFilters, selectedSortFilter);
            this.channelData = channelData;
        }
    }
}
