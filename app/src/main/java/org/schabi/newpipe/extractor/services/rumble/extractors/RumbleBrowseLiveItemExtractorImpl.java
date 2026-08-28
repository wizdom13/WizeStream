package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;

import static org.schabi.newpipe.extractor.ServiceList.Rumble;

/**
 * 20240123 - Live is working - https://rumble.com/browse/live
 */
public class RumbleBrowseLiveItemExtractorImpl extends RumbleItemsExtractorImpl {

    @Override
    protected Elements getAllItems(final Document doc) {
        return doc.select("div.videostream.thumbnail__grid-item");
    }

    @Override
    protected RumbleStreamType getStreamType(
            final Element element)
            throws ParsingException {
        final String liveStream =
                RumbleParsingHelper.extractSafely(false, "Could not extract the live thingy",
                        () -> element.getElementsByClass(
                                        "videostream__badge videostream__status"
                                                + " videostream__status--live")
                                .first().text());
        if ("LIVE".equals(liveStream)) {
            return RumbleStreamType.LIVE;
        }
        return RumbleStreamType.NORMAL;
    }

    @Override
    protected String getDuration(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(false, "Could not extract the duration",
                () -> element.getElementsByClass(
                        "videostream__status--duration").first().text());
    }

    @Override
    protected String getThumbUrl(
            final Element element,
            final String msg)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(false, msg + "url",
                () -> element.getElementsByClass("thumbnail__image").attr("src"));
    }

    @Override
    protected String getThumbWidth(
            final Element element,
            final String msg)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(false, msg + "width",
                () -> element.getElementsByClass("thumbnail__image").attr("width"));
    }

    @Override
    protected String getThumbHeight(
            final Element element,
            final String msg)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(false, msg + "height",
                () -> element.getElementsByClass("thumbnail__image").attr("height"));
    }

    @Override
    protected String getStreamTitle(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(true, "Could not extract the stream title",
                () -> element.select(
                        ".thumbnail__title").attr("title"));
    }

    @Override
    protected String getStreamUrl(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(true, "Could not extract the stream url",
                () -> Rumble.getBaseUrl() + element.select(
                        "a.videostream__link.link").attr("href"));
    }

    @Override
    protected String getStreamUploaderUrl(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(false, "Could not extract the uploader url",
                () -> Rumble.getBaseUrl()
                        + element.select("address > a ")
                        .attr("href"));
    }

    @Override
    protected String getStreamUploaderName(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(false, "Could not extract the uploader name",
                () -> element.select("div.channel__data > span")
                        .attr("title"));
    }

    @Override
    protected String getViewNumber(
            final Element element,
            final String viewCountErrMsg,
            final RumbleStreamType rumbleStreamType)
            throws ParsingException {
        if (rumbleStreamType == RumbleStreamType.LIVE) {
            return RumbleParsingHelper.extractSafely(false, viewCountErrMsg,
                    () -> element.getElementsByClass("videostream__number")
                            .first().text());
        } else {
            return RumbleParsingHelper.extractSafely(false, viewCountErrMsg,
                    () -> element.getElementsByClass("videostream__views")
                            .first().attr("data-views"));
        }
    }

    @Override
    protected String getTextualDate(
            final Element element,
            final RumbleStreamType rumbleStreamType)
            throws ParsingException {
        if (RumbleStreamType.LIVE == rumbleStreamType) {
            // on livestreams there is no date
            return null;
        } else {
            return RumbleParsingHelper
                    .extractSafely(false, "Could not extract the textual upload date",
                            () -> getClassValue(element,
                                    "videostream__data--subitem videostream__time",
                                    "datetime"));
        }
    }
}
