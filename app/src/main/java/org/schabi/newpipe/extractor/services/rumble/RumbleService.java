package org.schabi.newpipe.extractor.services.rumble;

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
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleChannelExtractor;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleChannelTabExtractor;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleCommentsExtractor;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleSearchExtractor;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleStreamExtractor;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleTrendingExtractor;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleTrendingShortsExtractor;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleCommentsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleTrendingLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import static java.util.Arrays.asList;
import static org.schabi.newpipe.extractor.ServiceList.Rumble;

public class RumbleService extends StreamingService {

    public RumbleService(final int id) {
        super(id, "Rumble", asList(ServiceInfo.MediaCapability.VIDEO,
                ServiceInfo.MediaCapability.AUDIO, ServiceInfo.MediaCapability.COMMENTS,
                ServiceInfo.MediaCapability.LIVE));
    }

    @Override
    public String getBaseUrl() {
        return "https://rumble.com";
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return RumbleStreamLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return RumbleChannelLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return RumbleChannelTabLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return null;
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return RumbleSearchQueryHandlerFactory.getInstance();
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        return RumbleStreamExtractor.factory(this, linkHandler);
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler) {
        return new RumbleChannelExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        return new RumbleChannelTabExtractor(this, linkHandler);
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler) {
        return null;
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler query) {
        return new RumbleSearchExtractor(this, query);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {

        final KioskList.KioskExtractorFactory trendingKioskExtractorFactory =
                new KioskList.KioskExtractorFactory() {
                    @Override
                    public KioskExtractor createNewKiosk(final StreamingService streamingService,
                                                         final String url,
                                                         final String kioskId)
                            throws ExtractionException {
                        if (kioskId.equals(RumbleTrendingShortsExtractor.KIOSK_SHORTS)) {
                            return new RumbleTrendingShortsExtractor(Rumble,
                                    RumbleTrendingLinkHandlerFactory.getInstance().fromId(kioskId),
                                    kioskId);
                        } else {
                            return new RumbleTrendingExtractor(Rumble,
                                    RumbleTrendingLinkHandlerFactory.getInstance().fromId(kioskId),
                                    kioskId);
                        }
                    }
                };

        final KioskList list = new KioskList(this);
        try {
            for (final String kioskId : RumbleTrendingLinkHandlerFactory.getInstance()
                    .getTrendingKioskIdsList()) {
                list.addKioskEntry(trendingKioskExtractorFactory,
                        RumbleTrendingLinkHandlerFactory.getInstance(), kioskId);
            }
            list.setDefaultKiosk(RumbleTrendingLinkHandlerFactory.DEFAULT_TRENDING);
        } catch (final Exception e) {
            throw new ExtractionException(e);
        }

        return list;
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return RumbleCommentsLinkHandlerFactory.getInstance();
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler urlIdHandler)
            throws ExtractionException {
        return new RumbleCommentsExtractor(this, urlIdHandler);
    }
}
