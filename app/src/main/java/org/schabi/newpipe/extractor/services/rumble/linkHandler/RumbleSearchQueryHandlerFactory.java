package org.schabi.newpipe.extractor.services.rumble.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.rumble.search.filter.RumbleFilters;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.UnsupportedEncodingException;
import java.util.List;

public final class RumbleSearchQueryHandlerFactory extends SearchQueryHandlerFactory {

    private static RumbleSearchQueryHandlerFactory instance = null;
    private final RumbleFilters searchFilters = new RumbleFilters();

    private RumbleSearchQueryHandlerFactory() {
    }

    public static synchronized RumbleSearchQueryHandlerFactory getInstance() {
        if (instance == null) {
            instance = new RumbleSearchQueryHandlerFactory();
        }
        return instance;
    }

    @Override
    public String getUrl(final String searchString,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter)
            throws ParsingException, UnsupportedOperationException {

        searchFilters.setSelectedSortFilter(selectedSortFilter);
        searchFilters.setSelectedContentFilter(selectedContentFilter);

        final String sortQuery = searchFilters.evaluateSelectedSortFilters();
        final String urlEndpoint = searchFilters.evaluateSelectedContentFilters();

        try {
            return urlEndpoint + Utils.encodeUrlUtf8(searchString) + sortQuery;
        } catch (final UnsupportedEncodingException e) {
            throw new ParsingException("Could not encode query", e);
        }
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
