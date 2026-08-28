package org.schabi.newpipe.extractor.services.bitchute;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteChannelExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteChannelTabExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteCommentsExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteRecommendedChannelKioskExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteSearchExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteStreamExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteSuggestionExtractor;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteTrendingKioskExtractor;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteCommentsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import static java.util.Arrays.asList;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.POPULAR;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.SUGGESTED;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.TRENDING_DAY;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.TRENDING_MONTH;
import static org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteKioskLinkHandlerFactory.TRENDING_WEEK;

public class BitchuteService extends StreamingService {

    public static final String BITCHUTE_LINK = "https://www.bitchute.com/";

    public BitchuteService(final int id) {
        super(id, "BitChute", asList(VIDEO, COMMENTS));
    }

    @Override
    public String getBaseUrl() {
        return BITCHUTE_LINK;
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return BitchuteStreamLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return BitchuteChannelLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return BitchuteChannelTabLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return null;
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return BitchuteSearchQueryHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return BitchuteCommentsLinkHandlerFactory.getInstance();
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler queryHandler) {
        return new BitchuteSearchExtractor(this, queryHandler);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return new BitchuteSuggestionExtractor(this);
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {

        final KioskList.KioskExtractorFactory trendingKioskExtractorFactory =
                new KioskList.KioskExtractorFactory() {
                    @Override
                    public KioskExtractor createNewKiosk(final StreamingService streamingService,
                                                         final String url, final String kioskId)
                            throws ExtractionException {
                        return new BitchuteTrendingKioskExtractor(
                                BitchuteService.this,
                                BitchuteKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                                kioskId);
                    }
                };

        final KioskList.KioskExtractorFactory recommendedChannelKioskExtractorFactory =
                new KioskList.KioskExtractorFactory() {
                    @Override
                    public KioskExtractor createNewKiosk(final StreamingService streamingService,
                                                         final String url, final String kioskId)
                            throws ExtractionException {
                        return new BitchuteRecommendedChannelKioskExtractor(
                                BitchuteService.this,
                                BitchuteKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                                kioskId);
                    }
                };

        final KioskList list = new KioskList(this);
        try {
            list.addKioskEntry(trendingKioskExtractorFactory,
                    BitchuteKioskLinkHandlerFactory.getInstance(), TRENDING_DAY);

            list.addKioskEntry(trendingKioskExtractorFactory,
                    BitchuteKioskLinkHandlerFactory.getInstance(), TRENDING_WEEK);

            list.addKioskEntry(trendingKioskExtractorFactory,
                    BitchuteKioskLinkHandlerFactory.getInstance(), TRENDING_MONTH);

            list.addKioskEntry(trendingKioskExtractorFactory,
                    BitchuteKioskLinkHandlerFactory.getInstance(), POPULAR);

            list.addKioskEntry(trendingKioskExtractorFactory,
                    BitchuteKioskLinkHandlerFactory.getInstance(), SUGGESTED);
            list.setDefaultKiosk(TRENDING_DAY);
        } catch (final Exception e) {
            throw new ExtractionException(e);
        }

        return list;
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return new BitchuteChannelExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return new BitchuteChannelTabExtractor(this, linkHandler);
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return null;
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler)
            throws ExtractionException {
        return new BitchuteStreamExtractor(this, linkHandler);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return new BitchuteCommentsExtractor(this, linkHandler);
    }
}
