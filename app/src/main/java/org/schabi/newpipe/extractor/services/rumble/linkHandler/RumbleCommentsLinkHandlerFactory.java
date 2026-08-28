package org.schabi.newpipe.extractor.services.rumble.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RumbleCommentsLinkHandlerFactory extends ListLinkHandlerFactory {

    private static final RumbleCommentsLinkHandlerFactory INSTANCE =
            new RumbleCommentsLinkHandlerFactory();

    private RumbleCommentsLinkHandlerFactory() {
    }

    public static RumbleCommentsLinkHandlerFactory getInstance() {
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
        return RumbleStreamLinkHandlerFactory.getInstance().getUrl(id);
    }

    @Override
    public String getId(final String url) throws ParsingException {
        // Delegation to avoid duplicate code, as we need the same id
        return RumbleStreamLinkHandlerFactory.getInstance().getId(url);
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
