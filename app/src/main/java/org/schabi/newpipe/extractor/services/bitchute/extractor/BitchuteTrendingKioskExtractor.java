package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.videos.ResultsStreamVideos;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.videos.Videos;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.POPULAR;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.SUGGESTED;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.TRENDING_DAY;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.TRENDING_MONTH;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.TRENDING_WEEK;

public class BitchuteTrendingKioskExtractor extends KioskExtractor<StreamInfoItem> {

    private static final int UNLIMITED_PAGES = 0;
    private int pageLimit;

    public BitchuteTrendingKioskExtractor(final StreamingService streamingService,
                                          final ListLinkHandler linkHandler,
                                          final String kioskId) {
        super(streamingService, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getId();
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ExtractionException, IOException {
        return getInfoItemsPage(BitchuteConstants.INITIAL_PAGE_NO);
    }

    private InfoItemsPage<StreamInfoItem> getInfoItemsPage(
            final String pageNo
    ) throws ExtractionException, IOException {
        final int currentPageNo = Integer.parseInt(pageNo);

        final ResultsStreamVideos results = getCategoryResultForQuery(
                remapper(), currentPageNo
        );
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        for (final Videos videos : results.getVideos()) {
            collector.commit(new BitchuteTrendingStreamInfoItemExtractor(videos));
        }

        if (results.getVideos().isEmpty() || pageLimit != UNLIMITED_PAGES) {
            return new InfoItemsPage<>(collector, null);
        } else {
            return new InfoItemsPage<>(collector, new Page(
                    getUrl(), String.valueOf(currentPageNo + 1))
            );
        }
    }

    public ResultsStreamVideos getCategoryResultForQuery(
            final String category,
            final int currentPageNumber) throws IOException, ExtractionException {

        final int offset = BitchuteSearchExtractor.LIMIT_RESULTS_PER_QUERY * currentPageNumber;


        final JsonBuilder<JsonObject> query = JsonObject.builder();
        query.value("selection", category);
        query.value("offset", offset);
        query.value("limit", BitchuteSearchExtractor.LIMIT_RESULTS_PER_QUERY);
        query.value("advertisable", true);

        return new ResultsStreamVideos(
                BitchuteParserHelper
                        .callJsonDjangoApi(query, ResultsStreamVideos.ENDPOINT));
    }

    private String remapper() {
        final String selector;
        switch (getId()) {
            case SUGGESTED:
                this.pageLimit = UNLIMITED_PAGES;
                selector = "popular";
                break;
            case POPULAR:
                this.pageLimit = UNLIMITED_PAGES;
                selector = "popular";
                break;
            case TRENDING_MONTH:
                this.pageLimit = 1;
                selector = "trending-month";
                break;
            case TRENDING_WEEK:
                this.pageLimit = 1;
                selector = "trending-week";
                break;
            case TRENDING_DAY:
            default:
                this.pageLimit = 1;
                selector = "trending-day";
        }
        return selector;
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return getInfoItemsPage(page.getId());
    }
}
