package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;

/**
 * 20240123 - editor-picks is working - https://rumble.com/editor-picks
 */
public class RumbleEditorPicksItemsExtractorImpl extends RumbleBrowseLiveItemExtractorImpl {
    @Override
    protected Elements getAllItems(final Document doc) {
        return doc.select("div.videostream");
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
                    () -> element.getElementsByClass("videostream__views--count")
                            .first().text());
        }
    }
}
