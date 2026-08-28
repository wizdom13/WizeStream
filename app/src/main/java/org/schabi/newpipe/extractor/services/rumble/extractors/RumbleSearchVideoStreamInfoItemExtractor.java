package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RumbleSearchVideoStreamInfoItemExtractor implements StreamInfoItemExtractor {
    String viewCount;
    String textualDate;
    String name;
    String url;
    List<Image> thumbUrls;
    String duration;
    String uploader;
    String uploaderUrl;
    DateWrapper uploadDate;
    boolean isLive;
    boolean isAd;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public RumbleSearchVideoStreamInfoItemExtractor(
            final String name, final String url,
            final List<Image> thumbUrls, final String viewCount,
            final String textualDate, final String duration,
            final String uploader, final String uploaderUrl,
            final DateWrapper uploadDate,
            final boolean isLive,
            final boolean isAd) {
        this.viewCount = viewCount;
        this.textualDate = textualDate;
        this.name = name;
        this.url = url;
        this.thumbUrls = thumbUrls;
        this.duration = duration;
        this.uploader = uploader;
        this.uploaderUrl = uploaderUrl;
        this.uploadDate = uploadDate;
        this.isLive = isLive;
        this.isAd = isAd;
    }

    @Override
    public StreamType getStreamType() {
        if (isLive) {
            return StreamType.LIVE_STREAM;
        }
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() {
        return this.isAd;
    }

    @Override
    public long getDuration() throws ParsingException {
        if (null == duration) {
            return -1;
        }
        return YoutubeParsingHelper.parseDurationString(duration);
    }

    @Override
    public long getViewCount() throws ParsingException {
        if (null == viewCount) {
            return -1;
        }
        try {
            return Utils.mixedNumberWordToLong(viewCount);
        } catch (final NumberFormatException e) {
            throw new ParsingException(e.getMessage());
        }
    }

    @Override
    public String getUploaderName() {
        return this.uploader;
    }

    @Override
    public String getUploaderUrl() {
        return this.uploaderUrl;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return textualDate;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return uploadDate;
    }

    @Override
    public String getName() throws ParsingException {
        return name;
    }

    @Override
    public String getUrl() throws ParsingException {
        return url;
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        return thumbUrls;
    }
}
