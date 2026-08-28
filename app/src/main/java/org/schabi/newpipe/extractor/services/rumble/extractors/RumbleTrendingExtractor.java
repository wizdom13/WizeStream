package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.rumble.RumbleParsingHelper;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleTrendingLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;

import javax.annotation.Nonnull;

public class RumbleTrendingExtractor extends KioskExtractor<StreamInfoItem> {

    private RumbleCommonCodeTrendingAndChannel sharedTrendingAndChannelCode;
    private Document doc;

    public RumbleTrendingExtractor(final StreamingService service,
                                   final ListLinkHandler linkHandler,
                                   final String kioskId) {
        super(service, linkHandler, kioskId);

        try {
            final RumbleItemsExtractorImpl itemsExtractor;
            if (RumbleTrendingLinkHandlerFactory.LIVE.equals(kioskId)) {
                itemsExtractor = new RumbleBrowseLiveItemExtractorImpl();
            } else if (RumbleTrendingLinkHandlerFactory.EDITOR_PICKS.equals(kioskId)) {
                itemsExtractor = new RumbleEditorPicksItemsExtractorImpl();
            } else {
                itemsExtractor = new RumbleSearchTrendingItemsExtractorImpl();
            }
            sharedTrendingAndChannelCode = new RumbleCommonCodeTrendingAndChannel(
                    getServiceId(), getUrl(), itemsExtractor);
        } catch (final ParsingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        doc = Jsoup.parse(getDownloader().get(
                        getUrl(),
                        RumbleParsingHelper.getMinimalHeaders(),
                        NewPipe.getPreferredLocalization())
                .responseBody());
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getId();
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (null == page) {
            return null;
        }

        doc = Jsoup.parse(getDownloader().get(
                        page.getUrl(),
                        RumbleParsingHelper.getMinimalHeaders(),
                        NewPipe.getPreferredLocalization())
                .responseBody());

        return sharedTrendingAndChannelCode.extractAndGetStreamInfoItemsFromPage(doc);
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        return sharedTrendingAndChannelCode.extractAndGetStreamInfoItemsFromPage(doc);
    }
}
