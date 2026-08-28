package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;

import static org.schabi.newpipe.extractor.ServiceList.Rumble;

/**
 * 20240123 -- 'Trending today' is working -- "https://rumble.com/videos?sort=views&date=today"
 * 20240123 -- 'Latest' is working -- "https://rumble.com/videos?date=this-week"
 * 20240123 -- 'leaderboard' is working -- "https://rumble.com/battle-leaderboard"
 */
public class RumbleSearchTrendingItemsExtractorImpl extends RumbleItemsExtractorImpl {
    @Override
    protected Elements getAllItems(final Document doc) {
        return doc.select("li.video-listing-entry");
    }

    @Override
    protected RumbleStreamType getStreamType(
            final Element element)
            throws ParsingException {
        final String liveStream =
                RumbleParsingHelper.extractSafely(false, "Could not extract the live thingy",
                        () -> getClassValue(element, "video-item--live", "data-value"));
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
                        () -> getClassValue(element, "video-item--duration", "data-value"));
    }

    @SuppressWarnings("checkstyle:InvalidJavadocPosition")
    @Override
    protected String getThumbUrl(
            final Element element,
            final String msg)
            throws ParsingException {
        String thumbUrl = RumbleParsingHelper.extractSafely(false, msg,
                () -> element.select("img.video-item--img").first().absUrl("src"));
        if (null == thumbUrl) {
            /** In case of the battle-leaderboard the image might be found here
             * see {@link RumbleTrendingLinkHandlerFactory.TODAYS_BATTLE_LEADERBOARD_TOP_50} */
            thumbUrl = RumbleParsingHelper.extractSafely(true, msg,
                    () -> element.select("img.video-item--img-img").first().absUrl("src"));
        }
        return thumbUrl;
    }

    @Override
    protected String getThumbWidth(
            final Element element,
            final String msg)
            throws ParsingException {
        return null;
    }

    @Override
    protected String getThumbHeight(
            final Element element,
            final String msg)
            throws ParsingException {
        return null;
    }

    @Override
    protected String getStreamTitle(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(true, "Could not extract the stream title",
                () -> element.select("h3.video-item--title").first().childNodes().get(0)
                        .toString());
    }

    @Override
    protected String getStreamUrl(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(true, "Could not extract the stream url",
                () -> Rumble.getBaseUrl()
                        + element.select("a.video-item--a").first().attr("href"));
    }

    @Override
    protected String getStreamUploaderUrl(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(true, "Could not extract the uploader url",
                () -> Rumble.getBaseUrl()
                        + element.select("address.video-item--by > a").first()
                        .attr("href"));
    }

    @Override
    protected String getStreamUploaderName(
            final Element element)
            throws ParsingException {
        return RumbleParsingHelper.extractSafely(true, "Could not extract the uploader name",
                () -> element.select("address.video-item--by").first()
                        // using textNodes() to remove the <svg> tag
                        .getElementsByTag("div").first().textNodes().get(0).text());
    }

    @SuppressWarnings("checkstyle:InvalidJavadocPosition")
    @Override
    protected String getViewNumber(
            final Element element,
            final String viewCountErrMsg,
            final RumbleStreamType rumbleStreamType)
            throws ParsingException {

        /** set {@value shouldThrowOnError} to 'false' as live events sometimes have no views
         * or obviously no duration at all. see {@link RumbleTrendingLinkHandlerFactory.LIVE} */
        return RumbleParsingHelper.extractSafely(false, "Could not extract the view count",
                        () -> element.getElementsByClass("video-item--views").first().text());
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
                    .extractSafely(true, "Could not extract the textual upload date",
                            () -> getClassValue(element, "video-item--time", "datetime"));
        }
    }
}
