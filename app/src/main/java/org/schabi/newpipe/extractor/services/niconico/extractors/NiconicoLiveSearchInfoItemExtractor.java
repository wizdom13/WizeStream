package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.net.URLDecoder;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.annotation.Nullable;

/**
 * Extracts NicoNico live items from either the current JSON ranking payload or the legacy card DOM.
 */
public class NiconicoLiveSearchInfoItemExtractor implements StreamInfoItemExtractor {
    @Nullable
    private final JsonObject jsonData;
    @Nullable
    private final Element elementData;

    public NiconicoLiveSearchInfoItemExtractor(final JsonObject data) {
        jsonData = data;
        elementData = null;
    }

    public NiconicoLiveSearchInfoItemExtractor(final Element element) {
        jsonData = null;
        elementData = element;
    }

    @Override
    public String getName() throws ParsingException {
        if (jsonData != null) {
            return jsonData.getString("title", "");
        }
        return requireElement()
                .select("a[class*=___program-card-title-anchor___]")
                .attr("title");
    }

    @Override
    public String getUrl() throws ParsingException {
        if (jsonData != null) {
            return jsonData.getString("watchPageUrl", "");
        }
        return requireElement()
                .select("a[class*=___program-card-title-anchor___]")
                .attr("href");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        if (jsonData != null) {
            return jsonData.getString("listingThumbnail", "");
        }
        return URLDecoder.decode(
                requireElement()
                        .select("img[class*=___program-card-thumbnail-image___]")
                        .attr("src"));
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.LIVE_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        return -1;
    }

    @Override
    public long getViewCount() throws ParsingException {
        if (jsonData != null) {
            return jsonData.getObject("statistics").getLong("watchCount");
        }
        try {
            return Long.parseLong(requireElement()
                    .select("span[class*=___program-card-statistics-text___] > span")
                    .get(1)
                    .text());
        } catch (final Exception e) {
            return -1;
        }
    }

    @Override
    public String getUploaderName() throws ParsingException {
        if (jsonData != null) {
            if (!jsonData.isNull("supplier")) {
                return jsonData.getObject("supplier").getString("name", "");
            }
            return jsonData.getObject("socialGroup").getString("name", "");
        }
        return requireElement()
                .select("p[class*=___program-card-provider-name___] "
                        + "> a[class*=___program-card-provider-name-link___]")
                .text();
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        if (jsonData != null) {
            if (!jsonData.isNull("supplier")) {
                final String providerId = jsonData.getObject("supplier")
                        .getString("programProviderId", "");
                return providerId.isEmpty() ? "" : NiconicoService.USER_URL + providerId;
            }

            final String socialGroupId =
                    jsonData.getObject("socialGroup").getString("id", "");
            return socialGroupId.isEmpty()
                    ? "" : NiconicoService.CHANNEL_URL + "channel/" + socialGroupId;
        }
        return requireElement()
                .select("p[class*=___program-card-provider-name___] "
                        + "> a[class*=___program-card-provider-name-link___]")
                .attr("href")
                .split("/live_programs")[0];
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if (jsonData != null) {
            if (!jsonData.isNull("supplier")) {
                return jsonData.getObject("supplier")
                        .getObject("icons")
                        .getString("uri150x150", "");
            }
            return jsonData.getObject("socialGroup").getString("thumbnailUrl", "");
        }
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        if (jsonData != null) {
            final long beginTime = jsonData.getLong("beginTime");
            return beginTime == 0
                    ? null
                    : Instant.ofEpochSecond(beginTime)
                            .atOffset(ZoneOffset.ofHours(9))
                            .toString();
        }
        try {
            return requireElement()
                    .select("span[class*=___program-card-statistics-text___] > span")
                    .get(0)
                    .text();
        } catch (final Exception e) {
            return null;
        }
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        if (jsonData == null) {
            return null;
        }
        final long beginTime = jsonData.getLong("beginTime");
        return beginTime == 0
                ? null
                : new DateWrapper(
                        Instant.ofEpochSecond(beginTime).atOffset(ZoneOffset.ofHours(9)));
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {
        return jsonData == null ? null : jsonData.getString("description", "");
    }

    private Element requireElement() throws ParsingException {
        if (elementData == null) {
            throw new ParsingException("Missing NicoNico live card data");
        }
        return elementData;
    }
}
