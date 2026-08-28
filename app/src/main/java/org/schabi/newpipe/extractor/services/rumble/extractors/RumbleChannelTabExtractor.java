package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;

import javax.annotation.Nonnull;
import java.io.IOException;

public class RumbleChannelTabExtractor extends ChannelTabExtractor {

    private final String userId;
    private RumbleCommonCodeTrendingAndChannel sharedTrendingAndChannelCode;
    private Document doc;

    public RumbleChannelTabExtractor(final StreamingService service,
                                         final ListLinkHandler linkHandler) {
        super(service, linkHandler);
        userId = getLinkHandler().getId();

        try {
            sharedTrendingAndChannelCode = new RumbleCommonCodeTrendingAndChannel(
                    getServiceId(), getUrl(), new RumbleChannelTabItemsExtractorImpl());
        } catch (final ParsingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        doc = RumbleParsingHelper.fetchParseValidate(downloader, getUrl());
    }

    @Nonnull
    @Override
    public String getId() {
        return userId;
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        return sharedTrendingAndChannelCode
                .extractAndGetInfoItemsFromPage(doc, getUrl());
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (null == page) {
            return null;
        }

        doc = Jsoup.parse(getDownloader().get(page.getUrl()).responseBody());
        return getInitialPage();
    }
}
