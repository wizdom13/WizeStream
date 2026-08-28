package org.schabi.newpipe.extractor.services.rumble.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.ContentAvailability;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Extractor for related Shorts items, used in getRelatedItems().
 */
class RumbleShortInfoItemExtractor implements StreamInfoItemExtractor {

    private final JsonObject item;
    private final JsonObject uploader;

    public RumbleShortInfoItemExtractor(@Nonnull JsonObject item) {
        this.item = item;
        this.uploader = item.getObject("by");
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM; // shorts are never live
    }

    @Override
    public boolean isAd() {
        return false; // assume related shorts are not ads
    }

    @Override
    public long getDuration() throws ParsingException {
        return item.getLong("duration", -1);
    }

    @Override
    public long getViewCount() throws ParsingException {
        return item.getLong("views", -1);
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return uploader.getString("name");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return uploader.getString("url");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return uploader.getBoolean("verified_badge", false);
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return item.getString("upload_date", null);
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        String date = getTextualUploadDate();
        if (date == null) return null;
        return new DateWrapper(java.time.OffsetDateTime.parse(date));
    }

    @Override
    public String getName() throws ParsingException {
        return item.getString("title");
    }

    @Override
    public String getUrl() throws ParsingException {
        return item.getString("url");
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        String thumbUrl = item.getString("thumb", null);
        if (thumbUrl == null) return Collections.emptyList();
        return List.of(new Image(thumbUrl,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Override
    public boolean isShortFormContent() throws ParsingException {
        return true; // all items are shorts
    }

    @Nonnull
    @Override
    public ContentAvailability getContentAvailability() throws ParsingException {
        return ContentAvailability.AVAILABLE;
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {
        return item.getString("processed_description", null);
    }
}
