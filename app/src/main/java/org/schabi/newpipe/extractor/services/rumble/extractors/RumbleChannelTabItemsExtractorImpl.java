package org.schabi.newpipe.extractor.services.rumble.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.rumble.settings.RumbleSettings;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class RumbleChannelTabItemsExtractorImpl extends RumbleBrowseLiveItemExtractorImpl {

    @Override
    public List<StreamInfoItemExtractor> extractStreamItems(final Document doc)
            throws ParsingException {
        final Element script = doc.selectFirst("rum-videos-grid script[type=application/json]");
        if (script == null) {
            return List.of();
        }

        final JsonArray items;
        try {
            items = JsonParser.object().from(script.data()).getArray("items");
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse the channel listing JSON", e);
        }
        if (items == null) {
            return List.of();
        }

        final List<StreamInfoItemExtractor> list = new LinkedList<>();
        for (final Object obj : items) {
            if (!(obj instanceof JsonObject)) {
                continue;
            }
            final JsonObject item = (JsonObject) obj;
            if (!"video".equals(item.getString("object_type"))) {
                continue;
            }
            list.add(toInfoItem(item));
        }
        return list;
    }

    private StreamInfoItemExtractor toInfoItem(final JsonObject item) {
        final boolean isLive = item.getBoolean("live");
        final JsonObject by = item.getObject("by");

        final String thumb = item.getString("thumb", "");
        final List<Image> thumbs = List.of(new Image(thumb,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));

        final String textualDate = item.getString("upload_date", null);
        DateWrapper uploadDate = null;
        if (textualDate != null) {
            uploadDate = new DateWrapper(OffsetDateTime.parse(textualDate), false);
        }

        final String views = isLive && !item.isNull("watching_now")
                ? String.valueOf(item.getInt("watching_now"))
                : String.valueOf(item.getInt("views"));

        final String duration = isLive ? null : secondsToClock(item.getInt("duration"));

        final boolean isAd = RumbleSettings.getInstance()
                .isSettingEnabled(RumbleSettings.HIDE_PREMIUM_STREAMS)
                && isPremium(item);

        return new RumbleSearchVideoStreamInfoItemExtractor(
                item.getString("title"),
                item.getString("url"),
                thumbs,
                views,
                textualDate,
                duration,
                by != null ? by.getString("name") : null,
                by != null ? by.getString("url") : null,
                uploadDate,
                isLive,
                isAd
        );
    }

    private static boolean isPremium(final JsonObject item) {
        return !"public".equals(item.getString("visibility"));
    }

    private static String secondsToClock(final long totalSeconds) {
        final long hours = totalSeconds / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
