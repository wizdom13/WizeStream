package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.rumble.settings.RumbleSettings;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

public abstract class RumbleItemsExtractorImpl /* extends RumbleCommonCodeTrendingAndSearching */ {

    public List<StreamInfoItemExtractor> extractStreamItems(final Document doc)
            throws ParsingException {
        final List<StreamInfoItemExtractor> list = new LinkedList<>();

        final Elements elements = getAllItems(doc);
        StreamInfoItemExtractor infoItemExtractor;

        for (final Element element : elements) {

            final RumbleStreamType rumbleStreamType = getStreamType(element);
            final String duration = getDuration(element);

            final String msg = "Could not extract the thumbnail ";
            final String thumbUrl = getThumbUrl(element, msg);
            final String thumbHeight = getThumbHeight(element, msg);
            final String thumbWidth = getThumbWidth(element, msg);
            final List<Image> thumbUrls = List.of(new Image(thumbUrl,
                    convertStringToInt(thumbHeight, Image.HEIGHT_UNKNOWN),
                    convertStringToInt(thumbWidth, Image.WIDTH_UNKNOWN),
                    Image.ResolutionLevel.UNKNOWN));

            final String title = getStreamTitle(element);
            final String url = getStreamUrl(element);
            final String uploaderUrl = getStreamUploaderUrl(element);
            final String uploader = getStreamUploaderName(element);


            final String viewCountErrMsg = "Could not extract the view count";
            final String views = getViewNumber(element, viewCountErrMsg, rumbleStreamType);

            final boolean isAd = RumbleSettings.getInstance()
                    .isSettingEnabled(RumbleSettings.HIDE_PREMIUM_STREAMS)
                    && isPremium(element);

            final String textualDate;
            DateWrapper uploadDate = null;
            textualDate = getTextualDate(element, rumbleStreamType);
            if (null != textualDate) {
                uploadDate = new DateWrapper(OffsetDateTime.parse(textualDate), false);
            }

            infoItemExtractor = new RumbleSearchVideoStreamInfoItemExtractor(
                    Parser.unescapeEntities(title, false),
                    url,
                    thumbUrls,
                    views,
                    textualDate,
                    duration,
                    uploader,
                    uploaderUrl,
                    uploadDate,
                    (RumbleStreamType.LIVE == rumbleStreamType),
                    isAd
            );

            list.add(infoItemExtractor);
        }

        return list;
    }

    protected String getClassValue(
            final Element element,
            final String className,
            final String attr) {
        return element.getElementsByClass(className).first().attr(attr);
    }

    /**
     * convert string number to integer.
     *
     * @param value        the value to convert
     * @param defaultValue if value is null than use this value
     * @return the converted value
     */
    private int convertStringToInt(
            @Nullable final String value,
            final int defaultValue) {
        if (null != value) {
            return Integer.parseInt(value);
        }
        return defaultValue;
    }

    // implement those methods
    protected abstract RumbleStreamType getStreamType(Element element) throws ParsingException;

    protected abstract String getThumbUrl(Element element, String msg) throws ParsingException;

    protected abstract String getThumbWidth(Element element, String msg) throws ParsingException;

    protected abstract String getThumbHeight(Element element, String msg) throws ParsingException;

    protected abstract String getStreamTitle(Element element) throws ParsingException;

    protected abstract String getStreamUrl(Element element) throws ParsingException;

    protected abstract String getStreamUploaderUrl(Element element) throws ParsingException;

    protected abstract String getStreamUploaderName(Element element) throws ParsingException;

    protected abstract String getViewNumber(
            Element element, String viewCountErrMsg, RumbleStreamType rumbleStreamType)
            throws ParsingException;

    protected abstract String getTextualDate(
            Element element, RumbleStreamType rumbleStreamType) throws ParsingException;

    protected abstract String getDuration(Element element) throws ParsingException;

    protected abstract Elements getAllItems(Document doc);

    protected boolean isPremium(final Element element) { // 20250322 shared code for now
        return !element.select("use[href$=premium__lock]").isEmpty();
    }

    protected enum RumbleStreamType {
        NORMAL, LIVE, FUTURE
    }
}
