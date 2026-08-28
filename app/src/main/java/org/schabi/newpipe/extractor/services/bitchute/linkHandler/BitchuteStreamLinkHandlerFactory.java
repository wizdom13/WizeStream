package org.schabi.newpipe.extractor.services.bitchute.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.MalformedURLException;
import java.net.URL;

public class BitchuteStreamLinkHandlerFactory extends LinkHandlerFactory {

    private static final BitchuteStreamLinkHandlerFactory INSTANCE =
            new BitchuteStreamLinkHandlerFactory();

    public static BitchuteStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private static String assertsID(final String id) throws ParsingException {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{11,}")) {
            throw new ParsingException("Given string is not a Bitchute Video ID");
        }
        return id;
    }

    @Override
    public String getUrl(final String id) {
        return BitchuteConstants.BASE_URL_VIDEO + "/" + id;
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
            if (splitPath[0].equalsIgnoreCase("video") || splitPath[0].equalsIgnoreCase("embed")) {
                return assertsID(splitPath[1]);
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new ParsingException("Error getting ID");
        }
        throw new ParsingException("Error url not suitable: " + urlString);
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
