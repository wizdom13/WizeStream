package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeTrendingExtractor;

public class YoutubeTrendingMusicExtractor extends YoutubeTrendingExtractor {

    public YoutubeTrendingMusicExtractor(final StreamingService streamingService,
                                         final ListLinkHandler linkHandler,
                                         final String kioskId) {
        super(streamingService, linkHandler, kioskId);
    }
}
