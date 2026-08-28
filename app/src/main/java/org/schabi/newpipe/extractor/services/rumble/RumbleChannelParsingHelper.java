package org.schabi.newpipe.extractor.services.rumble;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

public final class RumbleChannelParsingHelper {
    private RumbleChannelParsingHelper() {
    }

    public static String getChannelId(final Element doc) throws ParsingException {
        final Element idData = RumbleParsingHelper.extractSafely(true,
                "could not determine the channel Id",
                () -> doc.selectFirst("[data-slug][data-type]")
        );

        // java codechecker is wrong: if idData is null this code will not be executed.
        return getChannelIdAlreadySelected(idData);
    }

    public static String getChannelIdAlreadySelected(final Element idData) {
        final String channelName = idData.attr("data-slug");
        final String type = idData.attr("data-type");
        if ("channel".equals(type)) {
            return "c/" + channelName;
        } else if ("user".equals(type)) {
            return "user/" + channelName;
        }
        return null;
    }

    public static String getChannelName(
            final Element doc)
            throws ParsingException {
        final String name = RumbleParsingHelper.extractSafely(true,
                "Could not get channel name",
                () -> doc.getElementsByTag("title").first().text()
        );
        return name;
    }

}
