package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.channel.ResultsStreamChannel;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.channel.videos.ResultsStreamChannelVideos;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.channel.videos.Videos;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;
import org.schabi.newpipe.extractor.utils.BraveNewPipeExtractorUtils;

import java.io.IOException;

import javax.annotation.Nonnull;


public class BitchuteChannelTabExtractor extends ChannelTabExtractor {
    private ResultsStreamChannel channelData;

    public BitchuteChannelTabExtractor(final StreamingService service,
                                       final ListLinkHandler linkHandler) {
        super(service, linkHandler);
        if (linkHandler instanceof BraveNewPipeExtractorUtils.CustomTabListLinkHandler) {
            this.channelData = ((BraveNewPipeExtractorUtils
                    .CustomTabListLinkHandler<ResultsStreamChannel>) linkHandler)
                    .channelData;
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        return getPage(new Page(BitchuteConstants.INITIAL_PAGE_NO));
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return getInfoItemsPage(page.getUrl());
    }

    private InfoItemsPage<InfoItem> getInfoItemsPage(final String pageNo)
            throws ExtractionException, IOException {
        int currentPageNo = Integer.parseInt(pageNo);
        final ResultsStreamChannelVideos document =
                getChannelVideos(channelData.getChannelId(), currentPageNo);
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        for (final Videos video : document.getVideos()) {
            collector.commit(new BitchuteChannelStreamInfoItemExtractor(video) {

                @Override
                public String getUploaderName() throws ParsingException {
                    return channelData.getChannelName();
                }

                @Override
                public String getUploaderUrl() throws ParsingException {
                    return BitchuteParserHelper.prependBaseUrl(channelData.getChannelUrl());
                }

                @Override
                public boolean isUploaderVerified() throws ParsingException {
                    return false;
                }
            });
        }

        if (document.getVideos().size() < BitchuteSearchExtractor.LIMIT_RESULTS_PER_QUERY) {
            return new InfoItemsPage<>(collector, null);
        }
        currentPageNo++;
        return new InfoItemsPage<>(collector, new Page(String.valueOf(currentPageNo)));
    }

    public ResultsStreamChannelVideos getChannelVideos(
            final String channelId,
            final int currentPageNumber) throws IOException, ExtractionException {

        final int offset = BitchuteSearchExtractor.LIMIT_RESULTS_PER_QUERY * currentPageNumber;

        final JsonBuilder<JsonObject> query = JsonObject.builder();
        query.value("channel_id", channelId);
        query.value("offset", offset);
        query.value("limit", BitchuteSearchExtractor.LIMIT_RESULTS_PER_QUERY);

        return new ResultsStreamChannelVideos(
                BitchuteParserHelper.callJsonDjangoApi(
                        query, ResultsStreamChannelVideos.ENDPOINT));
    }

}
