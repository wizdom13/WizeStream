package org.schabi.newpipe.extractor.services.rumble.linkHandler;

import org.schabi.newpipe.extractor.brave.AttachException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;

import javax.annotation.Nonnull;

public final class RumbleStreamLinkHandlerFactory extends LinkHandlerFactory {

    public static final String BASE_URL = "https://rumble.com";
    private static final RumbleStreamLinkHandlerFactory INSTANCE =
            new RumbleStreamLinkHandlerFactory();
    /**
     * FIFO cache to keep some videoIds for getUrl(String id) working for shorts
     */
    private final CacheShortStreamIds cacheShortStreamIds = new CacheShortStreamIds();
    private final String patternMatchId = "^v[a-zA-Z0-9]{4,}-?";

    private RumbleStreamLinkHandlerFactory() {
    }

    public static RumbleStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private String assertsID(final String id) throws ParsingException {
        if (id == null || !id.matches(patternMatchId)) {
            throw new ParsingException("Given string is not a Rumble Video ID: " + id);
        }
        return id;
    }

    @Override
    public String getUrl(final String id) throws ParsingException {
        if (cacheShortStreamIds.has(id)) {
            return BASE_URL + "/shorts/" + assertsID(id);
        }
        return BASE_URL + "/" + assertsID(id);
    }

    @Override
    public String getId(final String urlString) throws ParsingException {
        final URL url;
        try {
            url = Utils.stringToURL(urlString);
            if (!url.getAuthority().equals(Utils.stringToURL(BASE_URL).getAuthority())
                    || !url.getProtocol().equals(Utils.stringToURL(BASE_URL).getProtocol())) {
                throw new MalformedURLException();
            }
        } catch (final MalformedURLException e) {
            final AttachException exception =
                    new AttachException("The given URL is not valid: " + urlString);
            exception.addExceptionData(e.getMessage());
            throw exception;
        }


        String path = url.getPath();
        boolean isShorts = false;

        if (path.startsWith("/shorts/v")) {
            path = path.substring(8);
            isShorts = true;
        } else if (path.startsWith("/v")) {
            path = path.substring(1);
        } else {
            throw new ParsingException("The given URL is not a Rumble video: " + urlString);
        }

        int dash = path.indexOf('-');
        String videoId = dash >= 2 ? path.substring(0, dash) : path;

        if (isShorts) {
            cacheShortStreamIds.enqueue(videoId);
        }

        return assertsID(videoId);
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

    private static class CacheShortStreamIds extends ArrayDeque<String> {

        private static final int CACHE_SIZE = 5;

        public synchronized void enqueue(@Nonnull String id) {
            remove(id); // move id to newest if already present
            addLast(id);

            if (size() > CACHE_SIZE) {
                removeFirst();
            }
        }

        public synchronized boolean has(@Nonnull String id) {
            return contains(id);
        }
    }
}
