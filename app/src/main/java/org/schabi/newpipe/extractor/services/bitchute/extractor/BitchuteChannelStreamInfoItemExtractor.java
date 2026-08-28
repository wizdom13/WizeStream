package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.channel.videos.Videos;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.brave.misc.BraveParsingHelper;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class BitchuteChannelStreamInfoItemExtractor implements StreamInfoItemExtractor {

    private final Videos video;

    public BitchuteChannelStreamInfoItemExtractor(final Videos video) {
        this.video = video;
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        return YoutubeParsingHelper.parseDurationString(video.getDuration());
    }

    @Override
    public long getViewCount() throws ParsingException {
        return video.getViewCount();
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return video.getDatePublished();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(BraveParsingHelper.parseDateFrom(getTextualUploadDate()));
    }

    @Override
    public String getName() throws ParsingException {
        return video.getVideoName();
    }

    @Override
    public String getUrl() throws ParsingException {
        return BitchuteParserHelper.prependBaseUrl(video.getVideoUrl());
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        return List.of(
                new Image(video.getThumbnailUrl(),
                        Image.HEIGHT_UNKNOWN,
                        Image.WIDTH_UNKNOWN,
                        Image.ResolutionLevel.UNKNOWN));
    }

    /**
     * create a json object. Basically this is useful for compact unit testing or exporting the data
     */
    @Override
    public String toString() {
        try {
            return "{"
                    + "\"streamType\": \"" + getStreamType().toString()
                    + "\", \"isAd\": \"" + isAd()
                    + "\", \"duration\": \"" + getDuration()
                    + "\", \"viewCount\": \"" + getViewCount()
                    + "\", \"uploadDate\": \"" + getUploadDate().offsetDateTime().toString()
                    + "\", \"uploaderName\": \"" + getUploaderName()
                    + "\", \"uploaderUrl\": \"" + getUploaderUrl()
                    + "\", \"name\": \"" + getName()
                    + "\", \"url\": \"" + getUrl()
                    + "\", \"thumbnailUrls\": \"" + getThumbnails()
                    + "\", \"isUploaderVerified\": \"" + isUploaderVerified()
                    + "\"}";
        } catch (final ParsingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
