package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.videos.Videos;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.brave.misc.BraveParsingHelper;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BitchuteTrendingStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private final Videos video;

    public BitchuteTrendingStreamInfoItemExtractor(final Videos video) {
        this.video = video;
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() {
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

    @Override
    public String getUploaderName() throws ParsingException {
        return video.getChannel().getChannelName();
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return BitchuteConstants.BASE_URL + video.getChannel().getChannelUrl();
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
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
        return BitchuteConstants.BASE_URL + video.getVideoUrl();
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
}
