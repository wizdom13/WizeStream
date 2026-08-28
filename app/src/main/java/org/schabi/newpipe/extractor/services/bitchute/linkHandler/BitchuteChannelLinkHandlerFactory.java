package org.schabi.newpipe.extractor.services.bitchute.linkHandler;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class BitchuteChannelLinkHandlerFactory extends ListLinkHandlerFactory {

    private static final BitchuteChannelLinkHandlerFactory INSTANCE =
            new BitchuteChannelLinkHandlerFactory();

    public static BitchuteChannelLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId(final String urlString) throws ParsingException, IllegalArgumentException {
        final URL url;
        try {
            url = Utils.stringToURL(urlString);
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("The given URL is not valid");
        }

        String path = url.getPath();

        if (!path.isEmpty()) {
            //remove leading "/"
            path = path.substring(1);
        }

        try {
            final String[] splitPath = path.split("/", 0);
            if (splitPath[0].equalsIgnoreCase("channel")) {
                return validateId(splitPath[1]);
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new ParsingException("Error getting ID");
        }
        throw new ParsingException("Error url not suitable: " + urlString);
    }

    private String validateId(final String id) throws ParsingException {
        if (!id.contains("/")) {
            return id;
        } else {
            throw new ParsingException("Id is not suitable: " + id);
        }
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter)
            throws ParsingException {
        return BitchuteConstants.BASE_URL_CHANNEL + "/" + validateId(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (final ParsingException e) {
            return false;
        }
    }
}
