package org.wisso.wizestream.desktop.backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.Frameset;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class ExtractorFacade {
    private static final long FEED_CACHE_MILLIS = 15 * 60_000L;
    private volatile Map<String, Object> cachedFeed;
    private volatile String cachedFeedSignature;
    private volatile long cachedFeedAt;

    ExtractorFacade() {
        NewPipe.init(new OkHttpDownloader());
    }

    List<Map<String, Object>> services() {
        return NewPipe.getServices().stream().map(service -> {
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", service.getServiceId());
            value.put("name", service.getServiceInfo().getName());
            value.put("capabilities", service.getServiceInfo().getMediaCapabilities().stream()
                    .map(Enum::name).toList());
            return value;
        }).toList();
    }

    List<Map<String, Object>> search(final int serviceId, final String query) throws Exception {
        if (query.isBlank() || query.length() > 500) throw new IllegalArgumentException("Invalid search query");
        final StreamingService service = NewPipe.getService(serviceId);
        final SearchInfo info = SearchInfo.getInfo(service, createSearchQuery(service, query));
        return info.getRelatedItems().stream().limit(60).map(this::searchItem).toList();
    }

    Map<String, Object> subscriptionFeed(
            final List<Map<String, Object>> subscriptions,
            final boolean refresh
    ) throws Exception {
        final String signature = subscriptionSignature(subscriptions);
        final long now = System.currentTimeMillis();
        final Map<String, Object> cached = cachedFeed;
        if (!refresh && cached != null && signature.equals(cachedFeedSignature)
                && now - cachedFeedAt < FEED_CACHE_MILLIS) {
            return cached;
        }

        if (subscriptions.isEmpty()) {
            final Map<String, Object> empty = feedResult(List.of(), 0, 0, now);
            cacheFeed(signature, now, empty);
            return empty;
        }

        final List<Callable<ChannelFeedResult>> tasks = subscriptions.stream()
                .map(subscription -> (Callable<ChannelFeedResult>) () -> channelFeed(subscription))
                .toList();
        final ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, tasks.size()));
        final Map<String, Map<String, Object>> uniqueItems = new LinkedHashMap<>();
        int failedChannels = 0;
        try {
            for (final Future<ChannelFeedResult> future : executor.invokeAll(tasks)) {
                try {
                    final ChannelFeedResult channel = future.get();
                    if (channel.failed()) failedChannels++;
                    for (final Map<String, Object> item : channel.items()) {
                        uniqueItems.putIfAbsent((String) item.get("url"), item);
                    }
                } catch (final ExecutionException error) {
                    failedChannels++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        final List<Map<String, Object>> items = new ArrayList<>(uniqueItems.values());
        items.sort(Comparator.comparingLong(ExtractorFacade::publishedAt).reversed());
        final List<Map<String, Object>> limited = List.copyOf(items.subList(0, Math.min(600, items.size())));
        final long refreshedAt = System.currentTimeMillis();
        final Map<String, Object> result = feedResult(
                limited, subscriptions.size(), failedChannels, refreshedAt);
        cacheFeed(signature, refreshedAt, result);
        return result;
    }

    SearchQueryHandler createSearchQuery(final StreamingService service, final String query)
            throws Exception {
        final SearchQueryHandlerFactory factory = service.getSearchQHFactory();
        return factory.fromQuery(query, defaultContentFilter(factory), Collections.emptyList());
    }

    private List<FilterItem> defaultContentFilter(final SearchQueryHandlerFactory factory) {
        final Filter availableFilters = factory.getAvailableContentFilter();
        if (availableFilters == null || availableFilters.getFilterGroups() == null) {
            return Collections.emptyList();
        }

        for (final FilterGroup group : availableFilters.getFilterGroups()) {
            if (group != null && group.filterItems != null && group.filterItems.length > 0) {
                return Collections.singletonList(group.filterItems[0]);
            }
        }

        return Collections.emptyList();
    }

    Map<String, Object> resolve(final String url, final JsonNode sponsorBlock) throws Exception {
        if (url == null || url.length() > 4_096 || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("Invalid stream URL");
        }
        configureSponsorBlock(sponsorBlock);
        return streamDetails(StreamInfo.getInfo(url));
    }

    private void configureSponsorBlock(final JsonNode sponsorBlock) {
        ServiceList.YouTube.setSponsorBlockApiSettings(null);
        if (sponsorBlock == null || !sponsorBlock.path("enabled").asBoolean(false)) return;

        final JsonNode categories = sponsorBlock.path("categories");
        final SponsorBlockApiSettings value = new SponsorBlockApiSettings();
        value.includeSponsorCategory = categoryEnabled(categories, "sponsor");
        value.includeIntroCategory = categoryEnabled(categories, "intro");
        value.includeOutroCategory = categoryEnabled(categories, "outro");
        value.includeInteractionCategory = categoryEnabled(categories, "interaction");
        value.includeSelfPromoCategory = categoryEnabled(categories, "self_promo");
        value.includeMusicCategory = categoryEnabled(categories, "non_music");
        value.includePreviewCategory = categoryEnabled(categories, "preview");
        value.includeFillerCategory = categoryEnabled(categories, "filler");
        value.includeHighlightCategory = categoryEnabled(categories, "highlight");
        ServiceList.YouTube.setSponsorBlockApiSettings(value);
    }

    private static boolean categoryEnabled(final JsonNode categories, final String id) {
        return categories.path(id).path("enabled").asBoolean(false);
    }

    Map<String, Object> streamDetails(final StreamInfo info) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("serviceId", info.getServiceId());
        value.put("url", info.getUrl());
        value.put("name", info.getName());
        value.put("uploaderName", info.getUploaderName());
        value.put("uploaderUrl", blankToNull(info.getUploaderUrl()));
        value.put("uploaderAvatarUrl", blankToNull(info.getUploaderAvatarUrl()));
        value.put("uploaderSubscriberCount", info.getUploaderSubscriberCount() < 0
                ? null : info.getUploaderSubscriberCount());
        value.put("thumbnailUrl", info.getThumbnailUrl());
        value.put("duration", info.getDuration());
        value.put("streamType", info.getStreamType().name());
        value.put("viewCount", info.getViewCount() < 0 ? null : info.getViewCount());
        value.put("likeCount", info.getLikeCount() < 0 ? null : info.getLikeCount());
        value.put("dislikeCount", info.getDislikeCount() < 0 ? null : info.getDislikeCount());
        value.put("publishedAt", info.getUploadDate() == null ? null
                : info.getUploadDate().offsetDateTime().toInstant().toEpochMilli());
        value.put("textualUploadDate", blankToNull(info.getTextualUploadDate()));
        final Description description = info.getDescription();
        value.put("description", description == null ? null : blankToNull(description.getContent()));
        value.put("descriptionType", description == null ? null : description.getType());
        value.put("relatedItems", info.getRelatedItems().stream()
                .filter(StreamInfoItem.class::isInstance).limit(40)
                .map(this::searchItem).toList());
        value.put("dashMpdUrl", blankToNull(info.getDashMpdUrl()));
        value.put("hlsUrl", blankToNull(info.getHlsUrl()));
        value.put("videoStreams", videoStreams(info));
        value.put("audioStreams", audioStreams(info));
        value.put("subtitles", subtitles(info));
        value.put("chapters", info.getStreamSegments().stream().map(this::chapter).toList());
        value.put("previewFrames", info.getPreviewFrames().stream().map(this::previewFrameset).toList());
        value.put("sponsorBlockSegments", sponsorBlockSegments(info));
        return value;
    }

    private Map<String, Object> chapter(final StreamSegment segment) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", segment.getTitle());
        value.put("startTimeSeconds", segment.getStartTimeSeconds());
        value.put("channelName", blankToNull(segment.getChannelName()));
        value.put("url", blankToNull(segment.getUrl()));
        value.put("previewUrl", blankToNull(segment.getPreviewUrl()));
        return value;
    }

    private Map<String, Object> previewFrameset(final Frameset frameset) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("urls", frameset.getUrls());
        value.put("frameWidth", frameset.getFrameWidth());
        value.put("frameHeight", frameset.getFrameHeight());
        value.put("totalCount", frameset.getTotalCount());
        value.put("durationPerFrame", frameset.getDurationPerFrame());
        value.put("framesPerPageX", frameset.getFramesPerPageX());
        value.put("framesPerPageY", frameset.getFramesPerPageY());
        return value;
    }

    private List<Map<String, Object>> sponsorBlockSegments(final StreamInfo info) {
        final SponsorBlockSegment[] segments = info.getSponsorBlockSegments();
        if (segments == null) return List.of();
        return java.util.Arrays.stream(segments).map(segment -> {
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("uuid", blankToNull(segment.uuid));
            value.put("startTime", segment.startTime);
            value.put("endTime", segment.endTime);
            value.put("category", desktopSponsorBlockCategory(segment));
            value.put("action", segment.action == null ? null : segment.action.getApiName());
            return value;
        }).filter(segment -> segment.get("category") != null && segment.get("action") != null).toList();
    }

    private String desktopSponsorBlockCategory(final SponsorBlockSegment segment) {
        if (segment.category == null) return null;
        return switch (segment.category) {
            case SPONSOR -> "sponsor";
            case INTRO -> "intro";
            case OUTRO -> "outro";
            case INTERACTION -> "interaction";
            case SELF_PROMO -> "self_promo";
            case NON_MUSIC -> "non_music";
            case PREVIEW -> "preview";
            case FILLER -> "filler";
            case HIGHLIGHT -> "highlight";
            default -> null;
        };
    }

    Map<String, Object> comments(final int serviceId, final String url) throws Exception {
        if (serviceId < 0 || url == null || url.length() > 4_096
                || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("Invalid stream URL");
        }
        final CommentsInfo info = CommentsInfo.getInfo(NewPipe.getService(serviceId), url);
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("disabled", info == null || info.isCommentsDisabled());
        value.put("items", info == null ? List.of() : info.getRelatedItems().stream()
                .limit(80).map(this::commentItem).toList());
        return value;
    }

    Map<String, Object> commentItem(final CommentsInfoItem item) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", blankToNull(item.getCommentId()));
        value.put("text", blankToNull(item.getCommentText()));
        value.put("uploaderName", blankToNull(item.getUploaderName()));
        value.put("uploaderAvatarUrl", blankToNull(item.getUploaderAvatarUrl()));
        value.put("uploaderUrl", blankToNull(item.getUploaderUrl()));
        value.put("publishedAt", item.getUploadDate() == null ? null
                : item.getUploadDate().offsetDateTime().toInstant().toEpochMilli());
        value.put("textualUploadDate", blankToNull(item.getTextualUploadDate()));
        value.put("likeCount", item.getLikeCount() < 0 ? null : item.getLikeCount());
        value.put("textualLikeCount", blankToNull(item.getTextualLikeCount()));
        value.put("replyCount", item.getReplyCount() < 0 ? null : item.getReplyCount());
        value.put("streamPosition", item.getStreamPosition() < 0 ? null : item.getStreamPosition());
        value.put("uploaderVerified", item.isUploaderVerified());
        value.put("heartedByUploader", item.isHeartedByUploader());
        value.put("pinned", item.isPinned());
        return value;
    }

    Map<String, Object> channelMetadata(final int serviceId, final String url) throws Exception {
        final ChannelInfo info = channelInfo(serviceId, url);
        final String avatarUrl = blankToNull(info.getAvatarUrl());
        final Long subscriberCount = info.getSubscriberCount() < 0 ? null : info.getSubscriberCount();
        if (avatarUrl == null && subscriberCount == null) {
            throw new IllegalArgumentException("Channel details are unavailable");
        }
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("avatarUrl", avatarUrl);
        value.put("subscriberCount", subscriberCount);
        return value;
    }

    Map<String, Object> channel(final int serviceId, final String url) throws Exception {
        final ChannelInfo info = channelInfo(serviceId, url);
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("serviceId", info.getServiceId());
        value.put("url", info.getUrl());
        value.put("name", info.getName());
        value.put("avatarUrl", blankToNull(info.getAvatarUrl()));
        value.put("bannerUrl", blankToNull(info.getBannerUrl()));
        value.put("subscriberCount", info.getSubscriberCount() < 0 ? null : info.getSubscriberCount());
        value.put("description", blankToNull(info.getDescription()));
        value.put("streams", info.getRelatedItems().stream().limit(60)
                .map(this::searchItem).toList());
        return value;
    }

    private ChannelInfo channelInfo(final int serviceId, final String url) throws Exception {
        if (serviceId < 0 || url == null || url.length() > 4_096
                || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("Invalid channel URL");
        }
        return ChannelInfo.getInfo(NewPipe.getService(serviceId), url);
    }

    Map<String, Object> searchItem(final InfoItem item) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", item.getInfoType().name());
        value.put("serviceId", item.getServiceId());
        value.put("url", item.getUrl());
        value.put("name", item.getName());
        value.put("thumbnailUrl", item.getThumbnailUrl());
        if (item instanceof StreamInfoItem stream) {
            value.put("uploaderName", stream.getUploaderName());
            value.put("uploaderUrl", blankToNull(stream.getUploaderUrl()));
            value.put("uploaderAvatarUrl", blankToNull(stream.getUploaderAvatarUrl()));
            value.put("duration", stream.getDuration());
            value.put("viewCount", stream.getViewCount() < 0 ? null : stream.getViewCount());
            value.put("publishedAt", stream.getUploadDate() == null ? null
                    : stream.getUploadDate().offsetDateTime().toInstant().toEpochMilli());
            value.put("textualUploadDate", blankToNull(stream.getTextualUploadDate()));
            value.put("streamType", stream.getStreamType().name());
            value.put("shortForm", stream.isShortFormContent());
        }
        return value;
    }

    private ChannelFeedResult channelFeed(final Map<String, Object> subscription) {
        try {
            final int serviceId = ((Number) subscription.get("serviceId")).intValue();
            final ChannelInfo info = channelInfo(serviceId, (String) subscription.get("url"));
            final String fallbackName = (String) subscription.get("name");
            final String fallbackAvatar = blankToNull((String) subscription.get("avatarUrl"));
            final List<Map<String, Object>> items = info.getRelatedItems().stream()
                    .filter(StreamInfoItem.class::isInstance).limit(24)
                    .map(this::searchItem)
                    .peek(item -> {
                        if (blankToNull((String) item.get("uploaderName")) == null) {
                            item.put("uploaderName", fallbackName);
                        }
                        if (blankToNull((String) item.get("uploaderAvatarUrl")) == null
                                && fallbackAvatar != null) {
                            item.put("uploaderAvatarUrl", fallbackAvatar);
                        }
                    }).toList();
            return new ChannelFeedResult(items, false);
        } catch (final Exception error) {
            return new ChannelFeedResult(List.of(), true);
        }
    }

    private static long publishedAt(final Map<String, Object> item) {
        final Object value = item.get("publishedAt");
        return value instanceof Number number ? number.longValue() : Long.MIN_VALUE;
    }

    private static String subscriptionSignature(final List<Map<String, Object>> subscriptions) {
        final StringBuilder value = new StringBuilder();
        for (final Map<String, Object> subscription : subscriptions) {
            value.append(subscription.get("serviceId")).append(':')
                    .append(subscription.get("url")).append('\n');
        }
        return value.toString();
    }

    private static Map<String, Object> feedResult(
            final List<Map<String, Object>> items,
            final int totalChannels,
            final int failedChannels,
            final long refreshedAt
    ) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("items", items);
        value.put("totalChannels", totalChannels);
        value.put("failedChannels", failedChannels);
        value.put("refreshedAt", refreshedAt);
        return value;
    }

    private void cacheFeed(final String signature, final long refreshedAt, final Map<String, Object> result) {
        cachedFeedSignature = signature;
        cachedFeedAt = refreshedAt;
        cachedFeed = result;
    }

    private record ChannelFeedResult(List<Map<String, Object>> items, boolean failed) { }

    private List<Map<String, Object>> videoStreams(final StreamInfo info) {
        final List<Map<String, Object>> result = new ArrayList<>();
        final List<VideoStream> streams = new ArrayList<>(info.getVideoStreams());
        streams.addAll(info.getVideoOnlyStreams());
        streams.stream().filter(Stream::isUrl).limit(80).map(this::videoStream).forEach(result::add);
        return result;
    }

    private List<Map<String, Object>> audioStreams(final StreamInfo info) {
        return info.getAudioStreams().stream().filter(Stream::isUrl).limit(80)
                .map(this::audioStream).toList();
    }

    private List<Map<String, Object>> subtitles(final StreamInfo info) {
        return info.getSubtitles().stream().filter(Stream::isUrl).limit(80)
                .map(this::subtitleStream).toList();
    }

    Map<String, Object> videoStream(final VideoStream stream) {
        final Map<String, Object> value = baseStream(stream);
        value.put("resolution", stream.getResolution());
        value.put("bitrate", stream.getBitrate());
        value.put("videoOnly", stream.isVideoOnly());
        value.put("codec", blankToNull(stream.getCodec()));
        value.put("audioTrackId", blankToNull(stream.getAudioTrackId()));
        value.put("audioTrackName", blankToNull(stream.getAudioTrackName()));
        value.put("audioLocale", blankToNull(stream.getAudioLocale()));
        return value;
    }

    Map<String, Object> audioStream(final AudioStream stream) {
        final Map<String, Object> value = baseStream(stream);
        value.put("bitrate", stream.getAverageBitrate());
        value.put("codec", blankToNull(stream.getCodec()));
        value.put("audioTrackId", blankToNull(stream.getAudioTrackId()));
        value.put("audioTrackName", blankToNull(stream.getAudioTrackName()));
        value.put("audioLocale", blankToNull(stream.getAudioLocale()));
        value.put("audioTrackType", stream.getAudioTrackType() == null
                ? null : stream.getAudioTrackType().name());
        return value;
    }

    Map<String, Object> subtitleStream(final SubtitlesStream stream) {
        final Map<String, Object> value = baseStream(stream);
        value.put("languageTag", stream.getLanguageTag());
        value.put("displayLanguage", stream.getLocale().getDisplayName());
        value.put("autoGenerated", stream.isAutoGenerated());
        return value;
    }

    private Map<String, Object> baseStream(final Stream stream) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", stream.getId());
        value.put("url", stream.getContent());
        value.put("format", stream.getFormat() == null ? null : stream.getFormat().getName());
        value.put("deliveryMethod", stream.getDeliveryMethod().name());
        return value;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
