package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeLockupStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeShortsInfoItemExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeTrendingExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;

/**
 * Exposes the Shorts shelves returned with YouTube's public trending browse response.
 *
 * <p>The public {@code /shorts} page is a watch sequence rather than a stable list endpoint.
 * The trending browse response usually provides a locale-aware Shorts collection. Some regions
 * omit that shelf, so the extractor falls back to YouTube's public Shorts search results.</p>
 */
public final class YoutubeShortsExtractor extends YoutubeTrendingExtractor {
    private static final String SHORTS_SEARCH_QUERY = "#shorts";

    private JsonObject searchFallbackData;

    public YoutubeShortsExtractor(final StreamingService service,
                                  final ListLinkHandler linkHandler,
                                  final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Nonnull
    @Override
    public String getName() {
        return "YouTube Shorts";
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        super.onFetchPage(downloader);
        searchFallbackData = null;

        final JsonObject trendingContent = getTrendingTabRenderer().getObject("content");
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(getServiceId());
        collectShorts(trendingContent, collector, new HashSet<>());
        if (collector.getItems().isEmpty()) {
            final byte[] body = JsonWriter.string(
                    prepareDesktopJsonBuilder(getExtractorLocalization(),
                            getExtractorContentCountry())
                            .value("query", SHORTS_SEARCH_QUERY)
                            .done())
                    .getBytes(UTF_8);
            searchFallbackData = getJsonPostResponse(
                    "search", body, getExtractorLocalization());
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Set<String> collectedUrls = new HashSet<>();
        collectShorts(getTrendingTabRenderer().getObject("content"), collector, collectedUrls);
        if (collector.getItems().isEmpty() && searchFallbackData != null) {
            collectShorts(searchFallbackData, collector, collectedUrls);
        }

        if (collector.getItems().isEmpty()) {
            throw new ParsingException("Could not get YouTube Shorts");
        }

        if (ServiceList.YouTube.getFilterTypes().contains("recommendations")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, null);
    }

    void collectShorts(final Object value,
                       final StreamInfoItemsCollector collector,
                       final Set<String> collectedUrls) {
        if (value instanceof JsonObject) {
            final JsonObject object = (JsonObject) value;
            if (object.has("shortsLockupViewModel")) {
                commitShortsLockup(object.getObject("shortsLockupViewModel"),
                        collector, collectedUrls);
                return;
            }
            if (object.has("reelItemRenderer")) {
                commitVideoRenderer(object.getObject("reelItemRenderer"),
                        collector, collectedUrls, true);
                return;
            }
            if (object.has("videoRenderer")) {
                commitVideoRenderer(object.getObject("videoRenderer"),
                        collector, collectedUrls, false);
                return;
            }
            if (object.has("lockupViewModel")) {
                commitLockupViewModel(object.getObject("lockupViewModel"),
                        collector, collectedUrls);
                return;
            }

            for (final Map.Entry<String, Object> entry : object.entrySet()) {
                collectShorts(entry.getValue(), collector, collectedUrls);
            }
        } else if (value instanceof JsonArray) {
            for (final Object item : (JsonArray) value) {
                collectShorts(item, collector, collectedUrls);
            }
        }
    }

    private void commitShortsLockup(final JsonObject shortsLockup,
                                    final StreamInfoItemsCollector collector,
                                    final Set<String> collectedUrls) {
        final YoutubeShortsInfoItemExtractor extractor =
                new YoutubeShortsInfoItemExtractor(shortsLockup);
        try {
            if (collectedUrls.add(extractor.getUrl())) {
                collector.commit(extractor);
            }
        } catch (final ParsingException e) {
            collector.commit(extractor);
        }
    }

    private void commitVideoRenderer(final JsonObject videoRenderer,
                                     final StreamInfoItemsCollector collector,
                                     final Set<String> collectedUrls,
                                     final boolean knownShort) {
        final YoutubeStreamInfoItemExtractor extractor =
                new YoutubeStreamInfoItemExtractor(videoRenderer, getTimeAgoParser());
        try {
            if ((knownShort || extractor.isShortFormContent())
                    && collectedUrls.add(extractor.getUrl())) {
                collector.commit(extractor);
            }
        } catch (final ParsingException e) {
            collector.commit(extractor);
        }
    }

    private void commitLockupViewModel(final JsonObject lockupViewModel,
                                       final StreamInfoItemsCollector collector,
                                       final Set<String> collectedUrls) {
        if (!"LOCKUP_CONTENT_TYPE_VIDEO".equals(
                lockupViewModel.getString("contentType"))) {
            return;
        }

        final String webPageType = lockupViewModel.getObject("rendererContext")
                .getObject("commandContext")
                .getObject("onTap")
                .getObject("innertubeCommand")
                .getObject("commandMetadata")
                .getObject("webCommandMetadata")
                .getString("webPageType");
        if (!"WEB_PAGE_TYPE_SHORTS".equals(webPageType)) {
            return;
        }

        final YoutubeLockupStreamInfoItemExtractor extractor =
                new YoutubeLockupStreamInfoItemExtractor(
                        lockupViewModel, getTimeAgoParser()) {
                    @Override
                    public boolean isShortFormContent() {
                        return true;
                    }
                };
        try {
            if (collectedUrls.add(extractor.getUrl())) {
                collector.commit(extractor);
            }
        } catch (final ParsingException e) {
            collector.commit(extractor);
        }
    }
}
