package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.rumble.RumbleChannelParsingHelper;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.BraveNewPipeExtractorUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.ListExtractor.ITEM_COUNT_UNKNOWN;

public class RumbleChannelExtractor extends ChannelExtractor {

    private Document doc;
    private Document about;

    public RumbleChannelExtractor(final StreamingService service,
                                  final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    private void checkIfChannelIsAvailable(final Response response)
            throws ContentNotAvailableException {
        if (response.responseCode() == 404) {
            String errMsg = RumbleParsingHelper.getErrFromTitle(this.doc);
            if (errMsg == null) {
                errMsg = "unknown, guess the channel is not available";
            }
            throw new ContentNotAvailableException(errMsg);

        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        doc = RumbleParsingHelper.fetchParseValidate(downloader, getUrl());
        final String about_link = RumbleParsingHelper.extractSafely(false,
            "",
            () -> doc.selectFirst("[href*='about']").attr("href")
        );
        if (null != about_link) {
            // somehow it is flawed within rumbles code. Some pages have complete url some not
            if (about_link.startsWith("http")) {
                about = Jsoup.parse(getDownloader().get(about_link).responseBody());
            } else {
                about = Jsoup.parse(getDownloader().get(getService().getBaseUrl() + about_link).responseBody());
            }
        }
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        try {
            return RumbleChannelLinkHandlerFactory.getInstance().getUrl(getId());
        } catch (final ParsingException e) {
            return super.getUrl();
        }
    }

    @Nonnull
    @Override
    public String getId() throws ParsingException {
        return RumbleChannelParsingHelper.getChannelId(doc);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return RumbleChannelParsingHelper.getChannelName(doc);
    }

    @Nonnull
    @Override
    public List<Image> getAvatars() throws ParsingException {
        final String url;
        url = RumbleParsingHelper.extractSafely(false,
                "Could not get avatar url",
                () -> doc.select("div[class~=(channel|listing)-header--content] img")
                        .first().attr("src")
        );

        if (null == url) { // try to determine if there is no avatar set at all
            if (!doc.select("[class~=channel-header--letter border-box]").isEmpty()) {
                return List.of();
            } else {
                throw new ParsingException(
                        "there is neither a avatar nor stated that there is none");
            }
        }
        return List.of(new Image(url,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Nonnull
    @Override
    public List<Image> getBanners() throws ParsingException {
        final String url = RumbleParsingHelper.extractSafely(false,
                "Could not get banner url",
                this::extractBannerUrl);
        return List.of(new Image(url,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    private String extractBannerUrl() {
        final Elements elements =
                doc.select("div[class~=(channel|listing)-header--backsplash] img");
        if (elements.isEmpty()) { // some have no banner so return null
            return null;
        } else {
            return elements.first().attr("src");
        }
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        return null; // there is no feed
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        final String errorMsg = "Could not get subscriber count";

        final String viewCount = RumbleParsingHelper.extractSafely(true,
                errorMsg,
                () -> doc.select("div.channel-header--title > span").first().text()
        );

        if (null != viewCount) {
            try {
                return Utils.mixedNumberWordToLong(viewCount);
            } catch (final NumberFormatException e) {
                throw new ParsingException(errorMsg, e);
            }
        } else {
            return ITEM_COUNT_UNKNOWN;
        }
    }

    @Override
    public String getDescription() throws ParsingException {
        if (null != about) {
            try {
                StringBuilder sb = new StringBuilder();
                org.jsoup.select.Elements el = about.select("div.channel-about--description > p");
                for (int i = 0; i < el.size(); i++) {
                    sb.append(el.get(i).text());
                    if (i < el.size() - 1) {
                        sb.append("\n\n");
                    }
                }
                return sb.toString();
            } catch (final Exception e) {
                throw new ParsingException("Could not get description: " + e);
            }
        }
        return "";
    }

    @Override
    public String getParentChannelName() throws ParsingException {
        return "";
    }

    @Override
    public String getParentChannelUrl() throws ParsingException {
        return "";
    }

    @Nonnull
    public List<Image> getParentChannelAvatars() throws ParsingException {
        return List.of();
    }

    @Override
    public boolean isVerified() throws ParsingException {
        final String verified = RumbleParsingHelper.extractSafely(false,
                "",
                () -> doc.select("svg[class~=(listing|channel)-header--verified]").first().text()
        );

        return verified != null;
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        final String id = getId();

        final Map<FilterItem, String> tab2Suffix =
                RumbleChannelTabLinkHandlerFactory.getTab2UrlSuffixes();

        return BraveNewPipeExtractorUtils.generateTabsFromSuffixMap(getUrl(), id, tab2Suffix, null);
    }
}
