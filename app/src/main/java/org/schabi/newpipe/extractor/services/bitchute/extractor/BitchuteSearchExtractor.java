package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.channels.Channels;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.channels.ResultsSearchChannels;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.videos.ResultsSearchVideos;
import com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.videos.Videos;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.brave.misc.BraveParsingHelper;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteParserHelper;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.misc.BitchuteHelpers;
import org.schabi.newpipe.extractor.services.bitchute.search.filter.BitchuteFilters;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BitchuteSearchExtractor extends SearchExtractor {

    public static final short LIMIT_RESULTS_PER_QUERY = 20;

    public BitchuteSearchExtractor(final StreamingService service,
                                   final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
    }

    @Nonnull
    @Override
    public String getSearchSuggestion() {
        return "";
    }

    @Override
    public boolean isCorrectedSearch() throws ParsingException {
        return false; // TODO evermind: this is just to get it compiled not verified
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList(); // TODO evermind verify what really should be done
    }

    @Nonnull
    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        return getPageInternal(new Page(getUrl(), BitchuteConstants.INITIAL_PAGE_NO));
    }

    private BitchuteFilters.BitchuteKindContentFilterItem getContentFilterWithQueryData() {
        final BitchuteFilters.BitchuteKindContentFilterItem filter =
                BitchuteSearchQueryHandlerFactory.getInstance()
                        .getSearchFilters().getFirstContentFilterItem();
        if (!(filter instanceof BitchuteFilters.BitchuteKindContentFilterItem)) {
            throw new RuntimeException("Somehow this is no valid BitChute content filter: "
                    + filter);
        }
        return filter;
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {

        final String sortQuery;
        JsonBuilder<JsonObject> sortQueryJson = null;
        final String endpoint;
        final String searchString = getLinkHandler().getId();
        int currentPageNumber = Integer.parseInt(page.getId());
        final BitchuteFilters.BitchuteKindContentFilterItem contentFilter =
                getContentFilterWithQueryData();


        sortQuery = contentFilter.getDataParams();
        sortQueryJson = contentFilter.getDataParamsNew();
        endpoint = contentFilter.endpoint;

        // request and retrieve the results via json
        final JsonObject jsonResponse = getSearchResultForQuery(
                searchString, sortQueryJson, endpoint, currentPageNumber);

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        final long noOfCurrentResults;
        final long total;

        switch (endpoint) {
            case ResultsSearchChannels.ENDPOINT:
                final ResultsSearchChannels channelResults =
                        new ResultsSearchChannels(jsonResponse);
                total = channelResults.getChannelCount();
                noOfCurrentResults = channelResults.getChannels().size();
                extractChannelsFromSearchResult(channelResults, collector);
                break;
            case ResultsSearchVideos.ENDPOINT:
            default:
                final ResultsSearchVideos resultsy =
                        new ResultsSearchVideos(jsonResponse);
                total = resultsy.getVideoCount();
                noOfCurrentResults = resultsy.getVideos().size();
                extractVideosFromSearchResult(resultsy, collector);
        }

        final int maxPages =
                (noOfCurrentResults != 0) ? (int) (total / LIMIT_RESULTS_PER_QUERY) : 0;
        if (maxPages > currentPageNumber) {
            return new InfoItemsPage<>(collector,
                    new Page(getUrl(), String.valueOf(++currentPageNumber)));
        } else {
            return new InfoItemsPage<>(collector, null);
        }
    }

    private void extractChannelsFromSearchResult(
            final ResultsSearchChannels results,
            final MultiInfoItemsCollector collector) {

        for (final Channels result : results.getChannels()) {
            final InfoItemExtractor infoItemExtractor =
                    new BitchuteQuickChannelInfoItemExtractor(
                            result.getChannelName(),
                            BitchuteParserHelper.prependBaseUrl(result.getChannelUrl()),
                            result.getThumbnailUrl(),
                            result.getDescription()
                    );

            collector.commit(infoItemExtractor);
        }
    }

    private void extractVideosFromSearchResult(
            final ResultsSearchVideos results,
            final MultiInfoItemsCollector collector) throws ParsingException {

        for (final Videos result : results.getVideos()) {
            final InfoItemExtractor infoItemExtractor;

            final String textualDate = result.getDatePublished();
            final String videoId = result.getVideoId();
            DateWrapper uploadDate = null;

            // textualDate is sometimes null. Observation 20220812
            if (textualDate != null) {
                try {
                    uploadDate = new DateWrapper(
                            BraveParsingHelper.parseDateFrom(textualDate));
                } catch (final Exception e) {
                    throw new ParsingException("Error Parsing Upload Date: "
                            + e.getMessage());
                }
            }
            infoItemExtractor = new BitchuteQuickStreamInfoItemExtractor(
                    result.getVideoName(),
                    BitchuteParserHelper.prependBaseUrl(result.getVideoUrl()),
                    result.getThumbnailUrl(),
                    result.getViewCount(),
                    textualDate,
                    result.getDuration(),
                    result.getChannel().getChannelName(),
                    BitchuteParserHelper.prependBaseUrl(result.getChannel().getChannelUrl()),
                    uploadDate
            );
            BitchuteHelpers.VideoDurationCache.addDurationToMap(videoId,
                    ((BitchuteQuickStreamInfoItemExtractor) infoItemExtractor)
                            .getDuration());

            collector.commit(infoItemExtractor);
        }
    }

    public JsonObject getSearchResultForQuery(
            final String searchString,
            final JsonBuilder<JsonObject> sortQueryJson,
            final String endpoint,
            final int currentPageNumber) throws IOException, ExtractionException {

        final int offset = LIMIT_RESULTS_PER_QUERY * currentPageNumber;


        sortQueryJson.value("limit", BitchuteSearchExtractor.LIMIT_RESULTS_PER_QUERY);
        sortQueryJson.value("offset", offset);
        sortQueryJson.value("query", searchString);

        return BitchuteParserHelper.callJsonDjangoApi(sortQueryJson, endpoint);
    }

    private static class BitchuteQuickStreamInfoItemExtractor implements StreamInfoItemExtractor {
        int viewCount;
        String textualDate;
        String name;
        String url;
        String thumbUrl;
        String duration;
        String uploader;
        String uploaderUrl;
        DateWrapper uploadDate;

        @SuppressWarnings("checkstyle:ParameterNumber")
        BitchuteQuickStreamInfoItemExtractor(final String name, final String url,
                                             final String thumbUrl, final int viewCount,
                                             final String textualDate, final String duration,
                                             final String uploader, final String uploaderUrl,
                                             final DateWrapper uploadDate) {
            this.viewCount = viewCount;
            this.textualDate = textualDate;
            this.name = name;
            this.url = url;
            this.thumbUrl = thumbUrl;
            this.duration = duration;
            this.uploader = uploader;
            this.uploaderUrl = uploaderUrl;
            this.uploadDate = uploadDate;
        }

        @Override
        public StreamType getStreamType() {
            return StreamType.VIDEO_STREAM;
        }

        @Override
        public boolean isAd() {
            return false;
        }

        @Override
        public long getDuration() throws ParsingException {
            return YoutubeParsingHelper.parseDurationString(duration);
        }

        @Override
        public long getViewCount() throws ParsingException {
            return viewCount;
        }

        @Override
        public String getUploaderName() {
            return this.uploader;
        }

        @Override
        public String getUploaderUrl() {
            return this.uploaderUrl;
        }

        @Override
        public boolean isUploaderVerified() throws ParsingException {
            return false; // TODO evermind: this is just to get it compiled not verified
        }

        @Nullable
        @Override
        public String getTextualUploadDate() {
            return textualDate;
        }

        @Nullable
        @Override
        public DateWrapper getUploadDate() throws ParsingException {
            return uploadDate;
        }

        @Override
        public String getName() throws ParsingException {
            return name;
        }

        @Override
        public String getUrl() throws ParsingException {
            return url;
        }

        @Nonnull
        @Override
        public List<Image> getThumbnails() throws ParsingException {
            return List.of(new Image(thumbUrl,
                    Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
        }
    }

    private static class BitchuteQuickChannelInfoItemExtractor implements ChannelInfoItemExtractor {

        String description;
        String name;
        String url;
        String thumbUrl;

        BitchuteQuickChannelInfoItemExtractor(final String name, final String url,
                                              final String thumbUrl, final String description) {
            this.description = description;
            this.name = name;
            this.url = url;
            this.thumbUrl = thumbUrl;
        }

        @Override
        public String getDescription() throws ParsingException {
            return description;
        }

        @Override
        public long getSubscriberCount() throws ParsingException {
            return -1;
        }

        @Override
        public long getStreamCount() throws ParsingException {
            return -1;
        }

        @Override
        public boolean isVerified() throws ParsingException {
            return false; // TODO evermind: this is just to get it compiled not verified
        }

        @Override
        public String getName() throws ParsingException {
            return name;
        }

        @Override
        public String getUrl() throws ParsingException {
            return url;
        }

        @Nonnull
        @Override
        public List<Image> getThumbnails() throws ParsingException {
            return List.of(new Image(thumbUrl,
                    Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
        }
    }
}
