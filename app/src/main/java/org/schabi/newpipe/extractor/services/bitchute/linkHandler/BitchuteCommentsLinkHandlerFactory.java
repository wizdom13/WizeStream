package org.schabi.newpipe.extractor.services.bitchute.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BitchuteCommentsLinkHandlerFactory extends ListLinkHandlerFactory {

    private static final BitchuteCommentsLinkHandlerFactory INSTANCE =
            new BitchuteCommentsLinkHandlerFactory();

    private BitchuteCommentsLinkHandlerFactory() {
    }

    public static BitchuteCommentsLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getUrl(final String id,
                         @Nonnull final List<FilterItem> contentFilter,
                         @Nullable final List<FilterItem> sortFilter) throws ParsingException {
        return getUrl(id);
    }

    @Override
    public String getUrl(final String id) throws ParsingException {
        return BitchuteStreamLinkHandlerFactory.getInstance().getUrl(id);
    }

    @Override
    public String getId(final String url) throws ParsingException {
        // Delegation to avoid duplicate code, as we need the same id
        return BitchuteStreamLinkHandlerFactory.getInstance().getId(url);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        try {
            getId(url);
            return true;
        } catch (final ParsingException e) {
            return false;
        }
    }
}
