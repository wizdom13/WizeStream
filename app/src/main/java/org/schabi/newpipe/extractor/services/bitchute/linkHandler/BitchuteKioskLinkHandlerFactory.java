package org.schabi.newpipe.extractor.services.bitchute.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.MalformedURLException;
import java.util.List;

import static org.schabi.newpipe.extractor.services.bitchute.BitchuteService.BITCHUTE_LINK;

public class BitchuteKioskLinkHandlerFactory extends ListLinkHandlerFactory {

    public static final String TRENDING_DAY = "Trending Today";
    public static final String TRENDING_MONTH = "Trending This Month";
    public static final String TRENDING_WEEK = "Trending This Week";
    public static final String POPULAR = "Popular";
    public static final String SUGGESTED = "Suggested";
    public static BitchuteKioskLinkHandlerFactory instance = new BitchuteKioskLinkHandlerFactory();

    public static BitchuteKioskLinkHandlerFactory getInstance() {
        return instance;
    }

    @Override
    public String getId(final String url) throws ParsingException {
        if (url.equals(BITCHUTE_LINK)) {
            return TRENDING_DAY;
        }
        try {
            final String s = Utils.stringToURL(url).getRef();
            return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
        } catch (final MalformedURLException e) {
            throw new ParsingException("Error parsing url id");
        }
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter)
            throws ParsingException {
        if (id.equals(TRENDING_DAY)) {
            return BITCHUTE_LINK;
        }
        return BITCHUTE_LINK + String.format("#%s/", id);
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        return url.startsWith(BITCHUTE_LINK);
    }
}
