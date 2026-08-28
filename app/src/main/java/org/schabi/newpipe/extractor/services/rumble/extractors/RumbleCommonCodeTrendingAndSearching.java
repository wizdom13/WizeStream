package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;

import java.util.List;

/**
 * common code shared between {@link RumbleSearchExtractor} and {@link RumbleTrendingExtractor}
 */
public class RumbleCommonCodeTrendingAndSearching {

    private final RumbleItemsExtractorImpl itemsExtractor;

    public RumbleCommonCodeTrendingAndSearching(final RumbleItemsExtractorImpl itemsExtractor1) {
        itemsExtractor = itemsExtractor1;
    }

    public Page getNewPageIfThereAreMoreThanOnePageResults(final int numberOfCollectedItems,
                                                           final Document doc,
                                                           final String urlPrefix) {

        Page nextPage = null;

        // -- check if there is a next page --
        // If numberOfCollectedItems is 0 than we have no results at all
        // -> assume no more pages
        if (numberOfCollectedItems > 0) {
            final Element nextLink = doc.selectFirst("link[rel=next]");
            final String nextPageUrl = nextLink != null ? nextLink.attr("href") : null;
            final boolean hasMorePages = nextPageUrl != null && !nextPageUrl.isEmpty();
            if (hasMorePages) {
                nextPage = new Page(nextPageUrl);
            }
        }
        return nextPage;
    }

    @SuppressWarnings("checkstyle:InvalidJavadocPosition")
    public List<StreamInfoItemExtractor> getSearchOrTrendingResultsItemList(final Document doc)
            throws ParsingException {
        return itemsExtractor.extractStreamItems(doc);
    }

    protected String getClassValue(final Element element,
                                   final String className,
                                   final String attr) {
        return element.getElementsByClass(className).first().attr(attr);
    }
}
