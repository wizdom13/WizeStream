package org.wisso.wizestream.desktop.backend;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExtractorFacade {
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

    Map<String, Object> resolve(final String url) throws Exception {
        if (url == null || url.length() > 4_096 || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("Invalid stream URL");
        }
        final StreamInfo info = StreamInfo.getInfo(url);
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("serviceId", info.getServiceId());
        value.put("url", info.getUrl());
        value.put("name", info.getName());
        value.put("uploaderName", info.getUploaderName());
        value.put("thumbnailUrl", info.getThumbnailUrl());
        value.put("duration", info.getDuration());
        value.put("streamType", info.getStreamType().name());
        value.put("dashMpdUrl", blankToNull(info.getDashMpdUrl()));
        value.put("hlsUrl", blankToNull(info.getHlsUrl()));
        value.put("videoStreams", videoStreams(info));
        value.put("audioStreams", audioStreams(info));
        value.put("subtitles", subtitles(info));
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

    private Map<String, Object> searchItem(final InfoItem item) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", item.getInfoType().name());
        value.put("serviceId", item.getServiceId());
        value.put("url", item.getUrl());
        value.put("name", item.getName());
        value.put("thumbnailUrl", item.getThumbnailUrl());
        if (item instanceof StreamInfoItem stream) {
            value.put("uploaderName", stream.getUploaderName());
            value.put("duration", stream.getDuration());
        }
        return value;
    }

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
