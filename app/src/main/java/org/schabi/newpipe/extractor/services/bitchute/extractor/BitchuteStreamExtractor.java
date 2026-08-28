package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.video.ResultsStreamVideo;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.video.counts.ResultsStreamVideoCounts;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.video.media.ResultsStreamVideoMedia;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.videos.ResultsStreamVideos;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.videos.Videos;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.brave.misc.BraveParsingHelper;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.stream.Stream.ID_UNKNOWN;

public class BitchuteStreamExtractor extends StreamExtractor {

    private ResultsStreamVideo streamVideoResults = null;
    private ResultsStreamVideoMedia streamVideoMediaResults = null;

    private ResultsStreamVideoCounts streamVideoViewCounts = null;

    private ResultsStreamVideos streamVideosSuggested;

    public BitchuteStreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final Response response = downloader.get(
                getUrl(),
                BitchuteParserHelper.getBasicHeader(), getExtractorLocalization());

        streamVideoResults = callApiAndGetResultsStreamVideo();
        streamVideoMediaResults = callApiAndGetResultsStreamVideoMedia();
        streamVideoViewCounts = callApiAndGetResultsStreamVideoCounts();
        streamVideosSuggested = callApiAndGetResultsStreamVideos();
    }

    private ResultsStreamVideos callApiAndGetResultsStreamVideos()
            throws ExtractionException, IOException {
        final JsonObject streamVideoResultsJson = BitchuteParserHelper.callJsonDjangoApi(
                JsonObject.builder()
                        .value("selection", "suggested")
                        .value("offset", 1)
                        .value("limit", 20)
                        .value("advertisable", true),
                ResultsStreamVideos.ENDPOINT
        );
        return new ResultsStreamVideos(streamVideoResultsJson);
    }

    private ResultsStreamVideoCounts callApiAndGetResultsStreamVideoCounts()
            throws ExtractionException, IOException {
        final JsonObject streamVideoResultsJson = BitchuteParserHelper.callJsonDjangoApi(
                JsonObject.builder().value("video_id", getId()),
                ResultsStreamVideoCounts.ENDPOINT
        );

        return new ResultsStreamVideoCounts(streamVideoResultsJson);
    }

    private ResultsStreamVideoMedia callApiAndGetResultsStreamVideoMedia()
            throws ExtractionException, IOException {
        final JsonObject streamVideoResultsJson = BitchuteParserHelper.callJsonDjangoApi(
                JsonObject.builder().value("video_id", getId()),
                ResultsStreamVideoMedia.ENDPOINT
        );
        return new ResultsStreamVideoMedia(streamVideoResultsJson);
    }

    ResultsStreamVideo callApiAndGetResultsStreamVideo()
            throws ExtractionException, IOException {
        final JsonObject streamVideoResultsJson = BitchuteParserHelper.callJsonDjangoApi(
                JsonObject.builder().value("video_id", getId()),
                ResultsStreamVideo.ENDPOINT
        );
        return new ResultsStreamVideo(streamVideoResultsJson);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return streamVideoResults.getVideoName();
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return streamVideoResults.getDatePublished();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(BraveParsingHelper.parseDateFrom(getTextualUploadDate()));
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        return List.of(new Image(streamVideoResults.getThumbnailUrl(),
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        return new Description(streamVideoResults.getDescription(), Description.HTML);
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        switch (streamVideoResults.getSensitivityId()) {
            case "safe":
                return StreamExtractor.NO_AGE_LIMIT;
            case "normal":
            default:
                return 16;
            case "nsfw":
                return 18;
            case "nsfl":
                return 21;
        }
    }

    @Override
    public long getLength() throws ParsingException {
        return YoutubeParsingHelper.parseDurationString(streamVideoResults.getDuration());
    }

    @Override
    public long getTimeStamp() {
        return 0;
    }

    @Override
    public long getViewCount() {
        return streamVideoViewCounts.getViewCount();
    }

    @Override
    public long getLikeCount() {
        return streamVideoViewCounts.getLikeCount();
    }

    @Override
    public long getDislikeCount() {
        return streamVideoViewCounts.getDislikeCount();
    }

    @Nullable
    @Override
    public StreamInfoItemsCollector getRelatedItems() throws ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final Videos video : streamVideosSuggested.getVideos()) {
            collector.commit(new BitchuteStreamRelatedInfoItemExtractor(video));
        }
        return collector;
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        return BitchuteParserHelper.prependBaseUrl(streamVideoResults.getChannel().getChannelUrl());
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        return streamVideoResults.getChannel().getChannelName();
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false; // TODO evermind: this is just to get it compiled not verified
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        return List.of(new Image(streamVideoResults.getChannel().getThumbnailUrl(),
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Nonnull
    @Override
    public String getSubChannelUrl() {
        return ""; // TODO evermind: this is just to get it compiled not verified
    }

    @Nonnull
    @Override
    public String getSubChannelName() {
        return ""; // TODO evermind: this is just to get it compiled not verified
    }

    @Nonnull
    @Override
    public String getDashMpdUrl() {
        return "";
    }

    @Nonnull
    @Override
    public String getHlsUrl() {
        return "";
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        try {
            final String videoUrl = streamVideoMediaResults.getMediaUrl();
            final String extension = videoUrl.substring(videoUrl.lastIndexOf(".") + 1);
            final MediaFormat format = MediaFormat.getFromSuffix(extension);

            final VideoStream.Builder builder = new VideoStream.Builder()
                    .setId(ID_UNKNOWN)
                    .setIsVideoOnly(false)
                    .setResolution("480p")
                    .setContent(videoUrl, true)
                    .setMediaFormat(format);

            return Collections.singletonList(builder.build());
        } catch (final Exception e) {
            throw new ParsingException("Error parsing video stream");
        }
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitlesDefault() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) {
        return Collections.emptyList();
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Nonnull
    @Override
    public String getHost() {
        return "";
    }

    @Nonnull
    @Override
    public Privacy getPrivacy() {
        return Privacy.OTHER; // TODO evermind: this is just to get it compiled not verified
    }

    @Nonnull
    @Override
    public String getCategory() throws ParsingException {
        return streamVideoResults.getCategoryId();
    }

    @Nonnull
    @Override
    public String getLicence() {
        return "";
    }

    @Nullable
    @Override
    public Locale getLanguageInfo() {
        return null;
    }

    @Nonnull
    @Override
    public List<String> getTags() {
        return streamVideoResults.getHashtags();
    }

    @Nonnull
    @Override
    public String getSupportInfo() {
        return "https://www.bitchute.com/help-us-grow/";
    }

    @Nonnull
    @Override
    public List<StreamSegment> getStreamSegments() throws ParsingException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }
}
