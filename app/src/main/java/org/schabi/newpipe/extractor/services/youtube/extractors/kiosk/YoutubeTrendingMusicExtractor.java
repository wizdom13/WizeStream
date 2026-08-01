package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.io.IOException;

import javax.annotation.Nonnull;

public class YoutubeTrendingMusicExtractor extends YoutubeChartsBaseKioskExtractor {
    static final String CHART_TYPE = "TRENDING_VIDEOS";

    public YoutubeTrendingMusicExtractor(final StreamingService streamingService,
                                         final ListLinkHandler linkHandler,
                                         final String kioskId) {
        super(streamingService, linkHandler, kioskId, CHART_TYPE);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        if (!SUPPORTED_COUNTRY_CODES.contains(getExtractorContentCountry().getCountryCode())) {
            throw new UnsupportedContentInCountryException(
                    "YouTube Charts does not support trending music in this country");
        }
        super.onFetchPage(downloader);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "Trending Music Videos";
    }
}
