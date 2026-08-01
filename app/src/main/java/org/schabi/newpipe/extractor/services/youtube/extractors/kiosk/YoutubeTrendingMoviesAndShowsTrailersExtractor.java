package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import javax.annotation.Nonnull;

public class YoutubeTrendingMoviesAndShowsTrailersExtractor
        extends YoutubeChartsBaseKioskExtractor {
    static final String CHART_TYPE = "TRENDING_MOVIES";

    public YoutubeTrendingMoviesAndShowsTrailersExtractor(final StreamingService streamingService,
                                                          final ListLinkHandler linkHandler,
                                                          final String kioskId) {
        super(streamingService, linkHandler, kioskId, CHART_TYPE);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "Trending Movie Trailers";
    }
}
