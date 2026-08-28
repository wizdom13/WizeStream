package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.channel.ResultsStreamChannel;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.BraveNewPipeExtractorUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public class BitchuteChannelExtractor extends ChannelExtractor {
    private ResultsStreamChannel channelData;

    public BitchuteChannelExtractor(final StreamingService service,
                                    final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final String channelId = getChannelID();
        if (channelData == null) {
            channelData = callApiAndGetResultsStreamChannel(channelId);
        }
    }

    public ResultsStreamChannel callApiAndGetResultsStreamChannel(
            final String channelId)
            throws ExtractionException, IOException {
        final JsonObject streamVideoResultsJson = BitchuteParserHelper.callJsonDjangoApi(
                JsonObject.builder().value("channel_id", channelId),
                ResultsStreamChannel.ENDPOINT);
        return new ResultsStreamChannel(streamVideoResultsJson);
    }

    private String getChannelID() throws ParsingException {
        final String[] urlSegments = getUrl().split("/");
        return urlSegments[urlSegments.length - 1];
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return channelData.getChannelName();
    }

    @Nonnull
    @Override
    public List<Image> getAvatars() throws ParsingException {
        return List.of(
                new Image(channelData.getThumbnailUrl(),
                        Image.HEIGHT_UNKNOWN,
                        Image.WIDTH_UNKNOWN,
                        Image.ResolutionLevel.UNKNOWN));
    }

    @Override
    public String getDescription() throws ParsingException {
        return channelData.getDescription();
    }

    @Override
    public String getParentChannelName() throws ParsingException {
        return null;
    }

    @Override
    public String getParentChannelUrl() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public List<Image> getParentChannelAvatars() throws ParsingException {
        return Collections.emptyList();
    }

    @Override
    public boolean isVerified() throws ParsingException {
        return false;
    }

    @Nonnull
    @Override
    public List<ListLinkHandler> getTabs() throws ParsingException {
        final String id = getId();

        final Map<FilterItem, String> tab2Suffix =
                BitchuteChannelTabLinkHandlerFactory.getTab2UrlSuffixes();

        return BraveNewPipeExtractorUtils
                .generateTabsFromSuffixMap(getUrl(), id, tab2Suffix, channelData);
    }

    @Override
    public long getSubscriberCount() throws ParsingException {
        return channelData.getSubscriberCount();
    }

    @Nonnull
    @Override
    public List<Image> getBanners() throws ParsingException {
        return getAvatars();
    }

    @Override
    public String getFeedUrl() {
        return null;
    }
}
