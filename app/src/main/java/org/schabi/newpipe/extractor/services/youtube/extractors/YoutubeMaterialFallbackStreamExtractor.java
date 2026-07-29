package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.jsoup.Jsoup;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.WatchDataCache;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getWebPlayerResponseSync;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;

/**
 * App-facing YouTube stream extractor fallbacks used by NewPipe Material.
 */
public class YoutubeMaterialFallbackStreamExtractor extends YoutubeRelatedFallbackStreamExtractor {
    private static final int MAX_DESCRIPTION_SCAN_DEPTH = 40;
    public YoutubeMaterialFallbackStreamExtractor(final StreamingService service,
                                                   final LinkHandler linkHandler,
                                                   final WatchDataCache watchDataCache) {
        super(service, linkHandler, watchDataCache);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        super.onFetchPage(downloader);

        if (getPrivateJsonObject("nextResponse") == null) {
            final Localization localization = new Localization("en");
            final ContentCountry contentCountry = getExtractorContentCountry();
            final byte[] body = JsonWriter.string(
                            prepareDesktopJsonBuilder(localization, contentCountry)
                                    .value("videoId", getId())
                                    .value("contentCheckOk", true)
                                    .value("racyCheckOk", true)
                                    .done())
                    .getBytes(StandardCharsets.UTF_8);
            final JsonObject recoveredNextResponse = getJsonPostResponse(
                    "next", body, localization);
            setPrivateJsonObject("nextResponse", recoveredNextResponse);
        }
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ParsingException originalError = null;
        Description originalDescription = null;
        try {
            originalDescription = super.getDescription();
            if (hasVisibleText(originalDescription)) {
                return originalDescription;
            }
        } catch (final ParsingException error) {
            originalError = error;
        }

        final String attributedDescription = findAttributedDescriptionContent();
        if (hasVisibleText(attributedDescription)) {
            return new Description(attributedDescription, Description.PLAIN_TEXT);
        }

        final String webDescription = fetchWebPlayerDescription();
        if (hasVisibleText(webDescription)) {
            return new Description(webDescription, Description.PLAIN_TEXT);
        }

        if (originalDescription != null) {
            return originalDescription;
        }
        if (originalError != null) {
            throw originalError;
        }
        return Description.EMPTY_DESCRIPTION;
    }

    private boolean hasVisibleText(@Nullable final Description description) {
        if (description == null || description.getContent() == null) {
            return false;
        }

        final String content = description.getType() == Description.HTML
                ? Jsoup.parse(description.getContent()).text()
                : description.getContent();
        return hasVisibleText(content);
    }

    private boolean hasVisibleText(@Nullable final String content) {
        return content != null && !content.trim().isEmpty();
    }

    @Nullable
    private String fetchWebPlayerDescription() {
        try {
            final Response response = getWebPlayerResponseSync(getId());
            final JsonObject webPlayerResponse = JsonUtils.toJsonObject(
                    getValidJsonResponseBody(response));
            final String shortDescription = webPlayerResponse
                    .getObject("videoDetails")
                    .getString("shortDescription", "");
            if (hasVisibleText(shortDescription)) {
                return shortDescription;
            }

            return scanForAttributedDescription(webPlayerResponse, 0);
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Nullable
    private String findAttributedDescriptionContent() {
        final String playerDescription = scanForAttributedDescription(playerResponse, 0);
        if (hasVisibleText(playerDescription)) {
            return playerDescription;
        }

        final String secondaryDescription = scanForAttributedDescription(
                getPrivateJsonObject("videoSecondaryInfoRenderer"), 0);
        if (hasVisibleText(secondaryDescription)) {
            return secondaryDescription;
        }

        return scanForAttributedDescription(getPrivateJsonObject("nextResponse"), 0);
    }

    @Nullable
    private JsonObject getPrivateJsonObject(final String fieldName) {
        try {
            final Field field = YoutubeStreamExtractor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (JsonObject) field.get(this);
        } catch (final Exception ignored) {
            return null;
        }
    }

    private void setPrivateJsonObject(final String fieldName, @Nonnull final JsonObject value)
            throws ExtractionException {
        try {
            final Field field = YoutubeStreamExtractor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(this, value);
        } catch (final ReflectiveOperationException error) {
            throw new ExtractionException("Could not restore YouTube " + fieldName, error);
        }
    }

    @Nullable
    private String scanForAttributedDescription(@Nullable final Object value, final int depth) {
        if (value == null || depth > MAX_DESCRIPTION_SCAN_DEPTH) {
            return null;
        }

        if (value instanceof JsonObject) {
            final JsonObject object = (JsonObject) value;
            final JsonObject attributedDescription = object.getObject("attributedDescription");
            if (attributedDescription != null && !attributedDescription.isEmpty()) {
                final String content = attributedDescription.getString("content", "");
                if (hasVisibleText(content)) {
                    return content;
                }
            }

            for (final Map.Entry<String, Object> entry : object.entrySet()) {
                final String description = scanForAttributedDescription(
                        entry.getValue(), depth + 1);
                if (hasVisibleText(description)) {
                    return description;
                }
            }
        } else if (value instanceof JsonArray) {
            for (final Object child : (JsonArray) value) {
                final String description = scanForAttributedDescription(child, depth + 1);
                if (hasVisibleText(description)) {
                    return description;
                }
            }
        }

        return null;
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        final JsonArray sources = getUploaderAvatarSources();
        if (sources == null || sources.isEmpty()) {
            return super.getUploaderAvatars();
        }

        final List<Image> images = new ArrayList<>(sources.size());
        for (final Object sourceValue : sources) {
            if (!(sourceValue instanceof JsonObject)) {
                continue;
            }

            final JsonObject source = (JsonObject) sourceValue;
            final String url = source.getString("url", "");
            if (url.isEmpty()) {
                continue;
            }

            final int width = source.getInt("width", Image.WIDTH_UNKNOWN);
            final int height = source.getInt("height", Image.HEIGHT_UNKNOWN);
            images.add(new Image(url, height, width, Image.ResolutionLevel.fromHeight(height)));
        }

        return images.isEmpty() ? super.getUploaderAvatars() : images;
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return super.getUploaderAvatarUrl();
    }

    @Nullable
    private JsonArray getUploaderAvatarSources() {
        final JsonObject nextResponse = getPrivateJsonObject("nextResponse");
        if (nextResponse == null) {
            return null;
        }

        final JsonObject owner = findVideoSecondaryInfoRenderer(nextResponse)
                .getObject("owner")
                .getObject("videoOwnerRenderer");
        if (owner.isEmpty()) {
            return null;
        }

        if (owner.has("avatarStack")) {
            return owner.getObject("avatarStack")
                    .getObject("avatarStackViewModel")
                    .getArray("avatars")
                    .getObject(0)
                    .getObject("avatarViewModel")
                    .getObject("image")
                    .getArray("sources");
        }

        return owner.getObject("thumbnail").getArray("thumbnails");
    }

    @Nonnull
    private JsonObject findVideoSecondaryInfoRenderer(@Nonnull final JsonObject nextResponse) {
        final JsonArray contents = nextResponse.getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents");
        if (contents == null) {
            return new JsonObject();
        }

        for (final Object contentValue : contents) {
            if (contentValue instanceof JsonObject) {
                final JsonObject content = (JsonObject) contentValue;
                if (content.has("videoSecondaryInfoRenderer")) {
                    return content.getObject("videoSecondaryInfoRenderer");
                }
            }
        }
        return new JsonObject();
    }

    @Nonnull
    @Override
    public String getSubChannelAvatarUrl() throws ParsingException {
        final String avatarUrl = fetchChannelAvatarUrl(getSubChannelUrl());
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            return avatarUrl;
        }

        return "";
    }

    @Nonnull
    @Override
    public List<Image> getSubChannelAvatars() throws ParsingException {
        final String avatarUrl = getSubChannelAvatarUrl();
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new Image(
                avatarUrl,
                Image.HEIGHT_UNKNOWN,
                Image.WIDTH_UNKNOWN,
                Image.ResolutionLevel.UNKNOWN));
    }

    @Nullable
    private String fetchChannelAvatarUrl(@Nullable final String channelUrl) {
        if (channelUrl == null || channelUrl.isEmpty()) {
            return null;
        }

        try {
            final YoutubeChannelExtractor channelExtractor = new YoutubeChannelExtractor(
                    getService(), getService().getChannelLHFactory().fromUrl(channelUrl));
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
            // Keep stream extraction usable even if the channel avatar fallback cannot be loaded.
        }
        return null;
    }
}
