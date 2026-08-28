package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.rumble.RumbleChannelParsingHelper;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.List;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.ListExtractor.ITEM_COUNT_UNKNOWN;
import static org.schabi.newpipe.extractor.ServiceList.Rumble;

class RumbleChannelSearchInfoItemExtractor implements ChannelInfoItemExtractor {

    private long subscriberCount;
    private String description = "";
    private String name;
    private String url;
    private String thumbUrl;
    private boolean verified;

    RumbleChannelSearchInfoItemExtractor(final Element element, final Document doc)
            throws ParsingException {
        extractData(element, doc);
    }

    private void extractData(final Element element, final Document doc) throws ParsingException {
        final Element data = element.select("div[class*=\"media-subscribe-and-notify\"]").first();
        if (data == null) {
            return; // skip this "<article>" as it does not contain any channel/user
        }

        // most channels have no description here
        this.description = RumbleParsingHelper.extractSafely(false,
                "",
                () -> element.select("p[class*=\"text-sm text-fjord\"]").first().text());

        this.name = RumbleParsingHelper.extractSafely(true,
                "Could not extract the channel name",
                () -> data.attr("data-title"));

        this.subscriberCount = extractSubscriberCount(element, doc);

        this.url = RumbleParsingHelper.extractSafely(true,
                "Could not extract the stream url",
                () -> Rumble.getBaseUrl() + "/"
                        + RumbleChannelParsingHelper.getChannelIdAlreadySelected(data));

        this.thumbUrl = extractTheThumbnailOfAChannelInASearchForChannels(element, doc);

        this.verified = !element.select("svg[class*=\"verification-badge-icon\"]").isEmpty();
    }

    private long extractSubscriberCount(final Element element, final Document document)
            throws ParsingException {

        final String errorMsg = "Could not get subscriber count";
        final String amountOfSubscribers = RumbleParsingHelper.extractSafely(true,
                errorMsg,
                () -> element.select("span[class*=\"text-sm text-fjord\"]").first().text());

        if (null != amountOfSubscribers) {
            try {
                return Utils.mixedNumberWordToLong(amountOfSubscribers.replace(",", ""));
            } catch (final NumberFormatException e) {
                throw new ParsingException(errorMsg, e);
            }
        } else {
            return ITEM_COUNT_UNKNOWN;
        }
    }

    // extract the thumbnail of a channel in a channel search
    private String extractTheThumbnailOfAChannelInASearchForChannels(final Element element,
                                                                     final Document document)
            throws ParsingException {
        return RumbleParsingHelper.extractThumbnail(document, element.toString(),
                () -> {
                    final String thumbUrlIdentifier = "i." + element
                            .select("i.user-image")
                            .attr("class")
                            .split(" ")[2];
                    return thumbUrlIdentifier;
                });
    }

    @Override
    public String getDescription() throws ParsingException {
        return description;
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return subscriberCount;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        return -1;
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return verified;
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
        return List.of(new Image(thumbUrl,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }
}
