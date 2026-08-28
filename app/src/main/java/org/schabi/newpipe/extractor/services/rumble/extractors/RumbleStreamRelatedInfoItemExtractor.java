package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.brave.AttachException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RumbleStreamRelatedInfoItemExtractor implements StreamInfoItemExtractor {

    private final Element element;
    private final TimeAgoParser parser;
    private Document doc;
    private String channelName;
    private String channelUrl;

    public RumbleStreamRelatedInfoItemExtractor(final TimeAgoParser parser, final Element element) {
        this.element = element;
        this.parser = parser;
    }

    public RumbleStreamRelatedInfoItemExtractor(final TimeAgoParser parser, final Element element,
                                                final Document doc) {
        this.element = element;
        this.parser = parser;
        this.doc = doc;
    }

    public RumbleStreamRelatedInfoItemExtractor(final TimeAgoParser parser, final Element element,
                                                final String channelName, final String channelUrl) {
        this.element = element;
        this.parser = parser;
        this.channelName = channelName;
        this.channelUrl = channelUrl;
    }

    @Override
    public StreamType getStreamType() {
        if (!element.select("small.medialist-live").isEmpty()) {
            return StreamType.LIVE_STREAM;
        }
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        try {
            if (getStreamType() == StreamType.LIVE_STREAM) {
                // this is a live stream, skip duration extraction
                return -1;
            }

            final Elements durationData = element.select("small.medialist-duration");
            if (durationData.isEmpty()) {
                // upcoming event marked DVR or similar -> no duration given
                if (!element.select("small.mediaList-upcoming").isEmpty()) {
                    return -1;
                }
                throw new Exception("Could not extract duration from the usual place");
            }
            final String durationString = durationData.first().text();
            final long duration =
                    RumbleParsingHelper.parseDurationStringForRelatedStreams(durationString);
            return duration;

        } catch (final Exception e) {
            final AttachException exception =
                    new AttachException("Error parsing duration: " + e);
            exception.addExceptionData("element", element.html());
            throw exception;
        }
    }

    @Override
    public long getViewCount() throws ParsingException {
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return getLiveStreamWatchingCount(element,
                    "small[class=mediaList-liveCount]");
        } else {
            try {
                return RumbleParsingHelper.getViewCount(element,
                        "div.video-counters--item.video-item--views");
            } catch (final ParsingException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    private long getLiveStreamWatchingCount(final Element elem, final String pattern)
            throws ParsingException {
        final String errorMsg = "Could not extract the live watching count";
        final String viewCount = RumbleParsingHelper.extractSafely(true, errorMsg,
                () -> elem.select(pattern).first().text());
        return Long.parseLong(Utils.removeNonDigitCharacters(viewCount));
    }

    @Override
    public String getUploaderName() throws ParsingException {
        try {
            if (channelName == null) {
                channelName = element.select("h4.mediaList-by-heading").first().text();
            }
            return channelName;
        } catch (final Exception e) {
            throw new ParsingException("Error parsing Stream uploader name");
        }
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        try {
            if (channelUrl == null) {
                final String classStr =
                        element.getElementsByClass("user-image").first().attr("class");
                channelUrl = RumbleParsingHelper
                        .moreTotalMessMethodToGenerateUploaderUrl(classStr, doc, getUploaderName());
            }
            return channelUrl;
        } catch (final Exception e) {
            throw new ParsingException(
                    "Error parsing uploader url: " + e.getMessage() + ". Cause:" + e.getCause());
        }
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

    @Override
    public String getName() throws ParsingException {
        try {
            final String title = element.select("h3.mediaList-heading").first().text();
            return title;
        } catch (final Exception e) {
            throw new ParsingException("Error parsing Stream title");
        }
    }

    @Override
    public String getUrl() throws ParsingException {
        try {
            final String url = element.select("a.mediaList-link").first().absUrl("href");
            return url;
        } catch (final Exception e) {
            throw new ParsingException("Error parsing Stream url");
        }
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        try {
            final String thumbUrl = element.select("img.mediaList-image").first().attr("src");
            return List.of(new Image(thumbUrl,
                    Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
        } catch (final Exception e) {
            throw new ParsingException("Error parsing thumbnail url");
        }
    }
}
