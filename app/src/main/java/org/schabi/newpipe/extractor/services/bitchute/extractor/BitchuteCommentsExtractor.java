package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;

import java.io.IOException;

import javax.annotation.Nonnull;

public class BitchuteCommentsExtractor extends CommentsExtractor {

    public BitchuteCommentsExtractor(final StreamingService service,
                                     final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage() throws ExtractionException,
            IOException {

        // comCount is set to 0. I think it only has to be set if there are many
        // comments and you have to retrieve them in multiple calls. (unproven)
        // From the js code in 'commentactions.js' the json key 'normalizedCommentCount' of
        // the endpoint '/api/get_comments/' if called with the parameter
        // additional parameter 'isNameValuesArrays=false' reveals the number of comments
        // There is also the '/api/get_comment_count/' endpoint that may be related. Nevertheless
        // in the browser I only could see that it always returns 0, maybe it will increase
        // if there are many comments --> TODO
        final var collector = extractAndCollectComments(0);
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(final Page page) throws ExtractionException,
            IOException {
        final var collector = extractAndCollectComments(0);
        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
    }

    private CommentsInfoItemsCollector extractAndCollectComments(final int comCount)
            throws ExtractionException, IOException {
        final JsonArray comments = BitchuteParserHelper.getComments(getId(), getUrl(), comCount);

        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(
                getServiceId());

        final String url = getUrl();
        for (final Object comment : comments) {
            collector.commit(new BitchuteCommentsInfoItemExtractor((JsonObject) comment, url));
        }

        return collector;
    }
}
