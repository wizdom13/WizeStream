package org.schabi.newpipe.extractor.services.peertube;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeSepiaStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class PeertubeParsingHelper {
    public static final String START_KEY = "start";
    public static final String COUNT_KEY = "count";
    public static final int ITEMS_PER_PAGE = 12;
    public static final String START_PATTERN = "start=(\\d*)";

    private PeertubeParsingHelper() {
    }

    public static void validate(final JsonObject json) throws ContentNotAvailableException {
        String error = json.getString("error");
        if (Utils.isBlank(error)) {
            error = json.getString("message");
        }
        if (Utils.isBlank(error)) {
            error = json.getString("detail");
        }
        if (!Utils.isBlank(error)) {
            throw new ContentNotAvailableException(error);
        }
    }

    /** Read both current PeerTube actor images and the legacy singular avatar. */
    public static String getAvatarUrl(final JsonObject actor, final String baseUrl) {
        if (actor == null) {
            return "";
        }
        String bestUrl = "";
        int bestWidth = -1;
        final JsonArray avatars = actor.getArray("avatars");
        if (avatars != null) {
            for (final Object value : avatars) {
                if (value instanceof JsonObject) {
                    final JsonObject avatar = (JsonObject) value;
                    final String url = getActorImageUrl(avatar, baseUrl);
                    final int width = avatar.getInt("width", 0);
                    if (!url.isEmpty() && (bestUrl.isEmpty() || width > bestWidth)) {
                        bestUrl = url;
                        bestWidth = width;
                    }
                }
            }
        }
        return bestUrl.isEmpty() ? getActorImageUrl(actor.getObject("avatar"), baseUrl)
                : bestUrl;
    }

    private static String getActorImageUrl(final JsonObject image, final String baseUrl) {
        if (image == null) {
            return "";
        }
        // fileUrl supports remote storage; path is deprecated but needed for older servers.
        for (final String key : new String[]{"fileUrl", "path"}) {
            final String value = image.getString(key);
            if (Utils.isBlank(value)) {
                continue;
            }
            try {
                final URI url = URI.create(baseUrl + "/").resolve(value);
                if (("https".equalsIgnoreCase(url.getScheme())
                        || "http".equalsIgnoreCase(url.getScheme())) && url.getHost() != null) {
                    return url.toString();
                }
            } catch (final IllegalArgumentException ignored) {
                // Try the legacy path if an optional image URL is malformed.
            }
        }
        return "";
    }

    public static OffsetDateTime parseDateFrom(final String textualUploadDate)
            throws ParsingException {
        try {
            return OffsetDateTime.ofInstant(Instant.parse(textualUploadDate), ZoneOffset.UTC);
        } catch (final DateTimeParseException e) {
            throw new ParsingException("Could not parse date: \"" + textualUploadDate + "\"", e);
        }
    }

    public static Page getNextPage(final String prevPageUrl, final long total) {
        final String prevStart;
        try {
            prevStart = Parser.matchGroup1(START_PATTERN, prevPageUrl);
        } catch (final Parser.RegexException e) {
            return null;
        }
        if (Utils.isBlank(prevStart)) {
            return null;
        }

        final long nextStart;
        try {
            nextStart = Long.parseLong(prevStart) + ITEMS_PER_PAGE;
        } catch (final NumberFormatException e) {
            return null;
        }

        if (nextStart >= total) {
            return null;
        } else {
            return new Page(prevPageUrl.replace(
                    START_KEY + "=" + prevStart, START_KEY + "=" + nextStart));
        }
    }

    public static void collectStreamsFrom(final InfoItemsCollector collector,
                                          final JsonObject json,
                                          final String baseUrl) throws ParsingException {
        collectStreamsFrom(collector, json, baseUrl, false);
    }

    /**
     * Collect stream from json with collector
     *
     * @param collector the collector used to collect information
     * @param json      the file to retrieve data from
     * @param baseUrl   the base Url of the instance
     * @param sepia     if we should use PeertubeSepiaStreamInfoItemExtractor
     */
    public static void collectStreamsFrom(final InfoItemsCollector collector,
                                          final JsonObject json,
                                          final String baseUrl,
                                          final boolean sepia) throws ParsingException {
        final JsonArray contents;
        try {
            contents = (JsonArray) JsonUtils.getValue(json, "data");
        } catch (final Exception e) {
            throw new ParsingException("Unable to extract list info", e);
        }

        for (final Object c : contents) {
            if (c instanceof JsonObject) {
                JsonObject item = (JsonObject) c;

                // PeerTube playlists have the stream info encapsulated in an "video" object
                if (item.has("video")) {
                    item = item.getObject("video");
                }

                final PeertubeStreamInfoItemExtractor extractor;
                if (sepia) {
                    extractor = new PeertubeSepiaStreamInfoItemExtractor(item, baseUrl);
                } else {
                    extractor = new PeertubeStreamInfoItemExtractor(item, baseUrl);
                }
                collector.commit(extractor);
            }
        }
    }

}
