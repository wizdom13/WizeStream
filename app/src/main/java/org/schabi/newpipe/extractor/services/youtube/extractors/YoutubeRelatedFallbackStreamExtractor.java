package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.WatchDataCache;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.fixThumbnailUrl;

/**
 * YouTube can move related items and uploader owner metadata between several watch-next containers.
 * The base extractor handles the classic paths; this wrapper keeps that behavior and falls back to
 * broader scans when the classic paths are absent or empty.
 */
public class YoutubeRelatedFallbackStreamExtractor extends YoutubeStreamExtractor {
    private static final int MAX_RELATED_ITEMS = 80;
    private static final int MAX_SCAN_DEPTH = 40;

    public YoutubeRelatedFallbackStreamExtractor(final StreamingService service,
                                                 final LinkHandler linkHandler,
                                                 final WatchDataCache watchDataCache) {
        super(service, linkHandler, watchDataCache);
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        try {
            final String avatarUrl = super.getUploaderAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                return avatarUrl;
            }
        } catch (final ParsingException ignored) {
            // Fall through to newer owner/avatar response layouts below.
        }

        final String channelAvatarUrl = fetchUploaderChannelAvatarUrl();
        if (channelAvatarUrl != null && !channelAvatarUrl.isEmpty()) {
            return channelAvatarUrl;
        }

        final String fallbackAvatarUrl = findUploaderAvatarUrl(getNextResponse());
        return fallbackAvatarUrl == null ? "" : fallbackAvatarUrl;
    }

    @Nullable
    private String fetchUploaderChannelAvatarUrl() {
        try {
            final YoutubeChannelExtractor channelExtractor = new YoutubeChannelExtractor(
                    getService(), getService().getChannelLHFactory().fromUrl(getUploaderUrl()));
            channelExtractor.fetchPage();

            final List<Image> avatars = channelExtractor.getAvatars();
            if (avatars == null || avatars.isEmpty()) {
                return null;
            }

            for (int i = avatars.size() - 1; i >= 0; i--) {
                final Image avatar = avatars.get(i);
                if (avatar != null && avatar.getUrl() != null && !avatar.getUrl().isEmpty()) {
                    return avatar.getUrl();
                }
            }
        } catch (final Exception ignored) {
            // Keep the stream page usable even if the channel fallback cannot be loaded.
        }
        return null;
    }

    @Nullable
    private String findUploaderAvatarUrl(@Nullable final JsonObject nextResponse) {
        if (nextResponse == null || nextResponse.isEmpty()) {
            return null;
        }

        final String channelId = getUploaderChannelId();
        final String uploaderName = getSafeUploaderName();
        return scanForUploaderAvatar(nextResponse, channelId, uploaderName, null, 0);
    }

    @Nullable
    private String getUploaderChannelId() {
        try {
            return playerResponse.getObject("videoDetails").getString("channelId", "");
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Nullable
    private String getSafeUploaderName() {
        try {
            return getUploaderName();
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Nullable
    private String scanForUploaderAvatar(final Object value,
                                         @Nullable final String channelId,
                                         @Nullable final String uploaderName,
                                         @Nullable final String parentKey,
                                         final int depth) {
        if (value == null || depth > MAX_SCAN_DEPTH) {
            return null;
        }

        if (value instanceof JsonObject) {
            final JsonObject object = (JsonObject) value;
            final String directAvatarUrl = avatarUrlFromKnownOwnerObject(
                    object, channelId, uploaderName, parentKey, depth);
            if (directAvatarUrl != null && !directAvatarUrl.isEmpty()) {
                return directAvatarUrl;
            }

            for (final Map.Entry<String, Object> entry : object.entrySet()) {
                final String avatarUrl = scanForUploaderAvatar(
                        entry.getValue(), channelId, uploaderName, entry.getKey(), depth + 1);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    return avatarUrl;
                }
            }
        } else if (value instanceof JsonArray) {
            final JsonArray array = (JsonArray) value;
            for (final Object child : array) {
                final String avatarUrl = scanForUploaderAvatar(
                        child, channelId, uploaderName, parentKey, depth + 1);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    return avatarUrl;
                }
            }
        }

        return null;
    }

    @Nullable
    private String avatarUrlFromKnownOwnerObject(final JsonObject object,
                                                 @Nullable final String channelId,
                                                 @Nullable final String uploaderName,
                                                 @Nullable final String parentKey,
                                                 final int depth) {
        if (object.has("videoOwnerRenderer")) {
            return extractAvatarUrl(object.getObject("videoOwnerRenderer"), depth + 1);
        }
        if (object.has("ownerViewModel")) {
            return extractAvatarUrl(object.getObject("ownerViewModel"), depth + 1);
        }
        if (object.has("channelThumbnailWithLinkRenderer")) {
            return extractAvatarUrl(object.getObject("channelThumbnailWithLinkRenderer"), depth + 1);
        }

        if (!looksLikeUploaderObject(object, channelId, uploaderName, parentKey)) {
            return null;
        }
        return extractAvatarUrl(object, depth + 1);
    }

    private boolean looksLikeUploaderObject(final JsonObject object,
                                            @Nullable final String channelId,
                                            @Nullable final String uploaderName,
                                            @Nullable final String parentKey) {
        if (object.has("videoId")) {
            return false;
        }

        final String key = parentKey == null ? "" : parentKey.toLowerCase(Locale.ROOT);
        if (key.contains("owner") || key.contains("author") || key.contains("uploader")) {
            return true;
        }

        final String objectText = object.toString();
        final boolean hasChannelId = channelId != null && !channelId.isEmpty()
                && objectText.contains(channelId);
        final boolean hasUploaderName = uploaderName != null && !uploaderName.isEmpty()
                && objectText.contains(uploaderName);
        if (!hasChannelId && !hasUploaderName) {
            return false;
        }

        final String lowerText = objectText.toLowerCase(Locale.ROOT);
        return lowerText.contains("avatar") || lowerText.contains("thumbnail")
                || lowerText.contains("image");
    }

    @Nullable
    private String extractAvatarUrl(final Object value, final int depth) {
        if (value == null || depth > MAX_SCAN_DEPTH) {
            return null;
        }

        if (value instanceof JsonObject) {
            final JsonObject object = (JsonObject) value;
            final String directUrl = imageUrlFromArray(object.getArray("sources"));
            if (directUrl != null && !directUrl.isEmpty()) {
                return directUrl;
            }
            final String thumbnailUrl = imageUrlFromArray(object.getArray("thumbnails"));
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                return thumbnailUrl;
            }

            final String avatarUrl = extractAvatarUrlFromKeys(object, depth, "avatar");
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                return avatarUrl;
            }
            final String imageUrl = extractAvatarUrlFromKeys(object, depth, "image");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                return imageUrl;
            }
            return extractAvatarUrlFromKeys(object, depth, "thumbnail");
        }

        if (value instanceof JsonArray) {
            final JsonArray array = (JsonArray) value;
            for (final Object child : array) {
                final String avatarUrl = extractAvatarUrl(child, depth + 1);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    return avatarUrl;
                }
            }
        }

        return null;
    }

    @Nullable
    private String extractAvatarUrlFromKeys(final JsonObject object,
                                            final int depth,
                                            final String keyNeedle) {
        for (final Map.Entry<String, Object> entry : object.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(keyNeedle)) {
                final String avatarUrl = extractAvatarUrl(entry.getValue(), depth + 1);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    return avatarUrl;
                }
            }
        }
        return null;
    }

    @Nullable
    private String imageUrlFromArray(@Nullable final JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }

        for (int i = array.size() - 1; i >= 0; i--) {
            final String url = array.getObject(i).getString("url", "");
            if (url != null && !url.isEmpty()) {
                return fixThumbnailUrl(url);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public MultiInfoItemsCollector getRelatedItems() throws ExtractionException {
        try {
            final MultiInfoItemsCollector collector = super.getRelatedItems();
            if (collector == null || !collector.getItems().isEmpty()) {
                return collector;
            }
        } catch (final ExtractionException ignored) {
            // Fall through to the broader scan below. The classic path is not always present.
        }

        final JsonObject nextResponse = getNextResponse();
        if (nextResponse == null || nextResponse.isEmpty()) {
            return new MultiInfoItemsCollector(getServiceId());
        }

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        final RelatedScanState state = new RelatedScanState(getId());
        scanForRelatedItems(nextResponse, collector, state, null);

        if (ServiceList.YouTube.getFilterTypes().contains("related_item")) {
            collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
        }
        return collector;
    }

    @Nullable
    private JsonObject getNextResponse() {
        try {
            final Field nextResponseField = YoutubeStreamExtractor.class.getDeclaredField("nextResponse");
            nextResponseField.setAccessible(true);
            return (JsonObject) nextResponseField.get(this);
        } catch (final Exception ignored) {
            return null;
        }
    }

    private void scanForRelatedItems(final Object value,
                                     final MultiInfoItemsCollector collector,
                                     final RelatedScanState state,
                                     @Nullable final String parentKey) {
        if (value == null || state.collectedCount >= MAX_RELATED_ITEMS) {
            return;
        }

        if (value instanceof JsonObject) {
            scanObjectForRelatedItems((JsonObject) value, collector, state, parentKey);
        } else if (value instanceof JsonArray) {
            final JsonArray array = (JsonArray) value;
            for (final Object child : array) {
                scanForRelatedItems(child, collector, state, parentKey);
                if (state.collectedCount >= MAX_RELATED_ITEMS) {
                    return;
                }
            }
        }
    }

    private void scanObjectForRelatedItems(final JsonObject object,
                                           final MultiInfoItemsCollector collector,
                                           final RelatedScanState state,
                                           @Nullable final String parentKey) {
        if (commitKnownRenderer(object, collector, state)) {
            return;
        }

        for (final Map.Entry<String, Object> entry : object.entrySet()) {
            if (isKnownRendererContainer(entry.getKey())) {
                continue;
            }
            scanForRelatedItems(entry.getValue(), collector, state, entry.getKey());
            if (state.collectedCount >= MAX_RELATED_ITEMS) {
                return;
            }
        }
    }

    private boolean commitKnownRenderer(final JsonObject object,
                                        final MultiInfoItemsCollector collector,
                                        final RelatedScanState state) {
        if (object.has("compactVideoRenderer")) {
            final JsonObject renderer = object.getObject("compactVideoRenderer");
            return commitStream(renderer, collector, state, false);
        }

        if (object.has("videoRenderer")) {
            final JsonObject renderer = object.getObject("videoRenderer");
            return commitStream(renderer, collector, state, false);
        }

        if (object.has("lockupViewModel")) {
            final JsonObject lockupViewModel = object.getObject("lockupViewModel");
            final String contentType = lockupViewModel.getString("contentType", "");
            if ("LOCKUP_CONTENT_TYPE_VIDEO".equals(contentType)) {
                return commitStream(lockupViewModel, collector, state, true);
            }
            if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(contentType)) {
                return commitLockupPlaylist(lockupViewModel, collector, state);
            }
        }

        if (object.has("compactRadioRenderer")) {
            return commitPlaylist(object.getObject("compactRadioRenderer"), collector, state);
        }

        if (object.has("compactPlaylistRenderer")) {
            return commitPlaylist(object.getObject("compactPlaylistRenderer"), collector, state);
        }

        return false;
    }

    private boolean commitStream(final JsonObject renderer,
                                 final MultiInfoItemsCollector collector,
                                 final RelatedScanState state,
                                 final boolean lockupRenderer) {
        final String videoId = renderer.getString("videoId",
                renderer.getString("contentId", ""));
        if (videoId == null || videoId.isEmpty()
                || Objects.equals(videoId, state.currentVideoId)
                || !state.seenVideoIds.add(videoId)) {
            return true;
        }

        final int beforeCount = collector.getItems().size();
        if (lockupRenderer) {
            collector.commit(new YoutubeLockupStreamInfoItemExtractor(renderer, timeAgoParser()));
        } else {
            final YoutubeStreamInfoItemExtractor extractor = new YoutubeStreamInfoItemExtractor(
                    renderer, timeAgoParser());
            extractor.setFallbackUploaderName("YouTube");
            extractor.setFallbackUploaderUrl("https://www.youtube.com");
            collector.commit(extractor);
        }

        if (collector.getItems().size() == beforeCount) {
            collector.commit(new FallbackRelatedStreamInfoItemExtractor(renderer, videoId));
        }
        if (collector.getItems().size() > beforeCount) {
            state.collectedCount++;
        }
        return true;
    }

    private boolean commitPlaylist(final JsonObject renderer,
                                   final MultiInfoItemsCollector collector,
                                   final RelatedScanState state) {
        final String key = renderer.getString("playlistId",
                renderer.getString("shareUrl", renderer.toString()));
        if (!state.seenPlaylistKeys.add(key)) {
            return true;
        }

        final int beforeCount = collector.getItems().size();
        collector.commit(new YoutubeMixOrPlaylistInfoItemExtractor(renderer));
        if (collector.getItems().size() > beforeCount) {
            state.collectedCount++;
        }
        return true;
    }

    private boolean commitLockupPlaylist(final JsonObject renderer,
                                         final MultiInfoItemsCollector collector,
                                         final RelatedScanState state) {
        final String key = renderer.getString("contentId", renderer.toString());
        if (!state.seenPlaylistKeys.add(key)) {
            return true;
        }

        final int beforeCount = collector.getItems().size();
        collector.commit(new YoutubeMixOrPlaylistLockupInfoItemExtractor(renderer));
        if (collector.getItems().size() > beforeCount) {
            state.collectedCount++;
        }
        return true;
    }

    @Nullable
    private TimeAgoParser timeAgoParser() {
        return null;
    }

    private boolean isKnownRendererContainer(final String key) {
        return "compactVideoRenderer".equals(key)
                || "videoRenderer".equals(key)
                || "lockupViewModel".equals(key)
                || "compactRadioRenderer".equals(key)
                || "compactPlaylistRenderer".equals(key);
    }

    private static final class RelatedScanState {
        private final String currentVideoId;
        private final Set<String> seenVideoIds = new HashSet<>();
        private final Set<String> seenPlaylistKeys = new HashSet<>();
        private int collectedCount;

        private RelatedScanState(final String currentVideoId) {
            this.currentVideoId = currentVideoId;
        }
    }

    private final class FallbackRelatedStreamInfoItemExtractor implements StreamInfoItemExtractor {
        private final JsonObject renderer;
        private final String videoId;

        private FallbackRelatedStreamInfoItemExtractor(final JsonObject renderer,
                                                       final String videoId) {
            this.renderer = renderer;
            this.videoId = videoId;
        }

        @Override
        public String getName() throws ParsingException {
            final String title = firstText(
                    renderer.getObject("title"),
                    renderer.getObject("headline"),
                    renderer.getObject("metadata").getObject("lockupMetadataViewModel")
                            .getObject("title"));
            if (title == null || title.isEmpty()) {
                return "YouTube video";
            }
            return title;
        }

        @Override
        public String getUrl() {
            return "https://www.youtube.com/watch?v=" + videoId;
        }

        @Override
        public String getThumbnailUrl() {
            final String thumbnailUrl = firstImageUrl(renderer);
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                return thumbnailUrl;
            }
            return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        }

        @Override
        public StreamType getStreamType() {
            return StreamType.VIDEO_STREAM;
        }

        @Override
        public long getDuration() {
            return -1;
        }

        @Override
        public long getViewCount() {
            return -1;
        }

        @Override
        public String getUploaderName() {
            final String uploader = firstText(
                    renderer.getObject("longBylineText"),
                    renderer.getObject("ownerText"),
                    renderer.getObject("shortBylineText"));
            return uploader == null || uploader.isEmpty() ? "YouTube" : uploader;
        }

        @Override
        public String getUploaderUrl() {
            return "https://www.youtube.com";
        }

        @Nullable
        @Override
        public String getTextualUploadDate() {
            return null;
        }

        @Nullable
        @Override
        public DateWrapper getUploadDate() {
            return null;
        }
    }

    @Nullable
    private String firstImageUrl(final JsonObject renderer) {
        final String normalThumbnail = imageUrlFromArray(
                renderer.getObject("thumbnail").getArray("thumbnails"));
        if (normalThumbnail != null && !normalThumbnail.isEmpty()) {
            return normalThumbnail;
        }

        final String lockupThumbnail = imageUrlFromArray(renderer.getObject("contentImage")
                .getObject("thumbnailViewModel")
                .getObject("image")
                .getArray("sources"));
        if (lockupThumbnail != null && !lockupThumbnail.isEmpty()) {
            return lockupThumbnail;
        }

        return imageUrlFromArray(renderer.getArray("sources"));
    }

    @Nullable
    private String firstText(final JsonObject... textObjects) {
        for (final JsonObject textObject : textObjects) {
            final String value = textFromObject(textObject);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private String textFromObject(@Nullable final JsonObject textObject) {
        if (textObject == null || textObject.isEmpty()) {
            return null;
        }

        final String simpleText = textObject.getString("simpleText", "");
        if (simpleText != null && !simpleText.isEmpty()) {
            return simpleText;
        }

        final String content = textObject.getString("content", "");
        if (content != null && !content.isEmpty()) {
            return content;
        }

        final JsonArray runs = textObject.getArray("runs");
        if (runs != null && !runs.isEmpty()) {
            final StringBuilder builder = new StringBuilder();
            for (final Object runObject : runs) {
                if (runObject instanceof JsonObject) {
                    final String text = ((JsonObject) runObject).getString("text", "");
                    if (text != null) {
                        builder.append(text);
                    }
                }
            }
            return builder.toString().trim();
        }
        return null;
    }
}
