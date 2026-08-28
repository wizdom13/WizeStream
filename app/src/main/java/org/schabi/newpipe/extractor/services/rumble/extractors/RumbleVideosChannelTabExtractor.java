package org.schabi.newpipe.extractor.services.rumble.extractors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

import javax.annotation.Nonnull;

public class RumbleVideosChannelTabExtractor extends ChannelTabExtractor {

    private RumbleCommonCodeTrendingAndChannel sharedTrendingAndChannelCode;
    private Document doc;

    protected RumbleVideosChannelTabExtractor(
            @Nonnull final StreamingService service,
            @Nonnull final ListLinkHandler linkHandler) {
        super(service, linkHandler);

        try {
            sharedTrendingAndChannelCode =
                    new RumbleCommonCodeTrendingAndChannel(getServiceId(), getUrl(), null);
        } catch (final ParsingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ListExtractor.InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (null == page) {
            return null;
        }

        doc = Jsoup.parse(getDownloader().get(page.getUrl()).responseBody());
        return sharedTrendingAndChannelCode.extractAndGetInfoItemsFromPage(doc);
    }

    @Nonnull
    @Override
    public ListExtractor.InfoItemsPage<InfoItem> getInitialPage()
            throws IOException, ExtractionException {
        return sharedTrendingAndChannelCode.extractAndGetInfoItemsFromPage(doc);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

    }
}
