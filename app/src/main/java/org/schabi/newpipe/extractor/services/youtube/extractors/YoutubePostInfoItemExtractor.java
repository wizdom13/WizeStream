package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.post.PostInfoItemExtractor;
import org.schabi.newpipe.extractor.utils.HtmlParser;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
        .getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;

/** Normalizes a YouTube backstage post renderer into a service-independent post item. */
public final class YoutubePostInfoItemExtractor implements PostInfoItemExtractor {
    private static final String YOUTUBE_URL = "https://www.youtube.com";

    @Nonnull
    private final JsonObject postRenderer;
    @Nonnull
    private final TimeAgoParser timeAgoParser;

    public YoutubePostInfoItemExtractor(@Nonnull final JsonObject postRenderer,
                                        @Nonnull final TimeAgoParser timeAgoParser) {
        this.postRenderer = postRenderer;
        this.timeAgoParser = timeAgoParser;
    }

    @Override
    public String getPostId() throws ParsingException {
        final String postId = postRenderer.getString("postId");
        if (Utils.isBlank(postId)) {
            throw new ParsingException("Could not get post id");
        }
        return postId;
    }

    @Override
    public String getUrl() throws ParsingException {
        final String endpointUrl = postRenderer.getObject("navigationEndpoint")
                .getObject("commandMetadata")
                .getObject("webCommandMetadata")
                .getString("url");
        if (!Utils.isBlank(endpointUrl)) {
            return absoluteUrl(endpointUrl);
        }
        return YOUTUBE_URL + "/post/" + getPostId();
    }

    @Override
    public String getName() throws ParsingException {
        return getUploaderName();
    }

    @Override
    public String getContent() throws ParsingException {
        try {
            final JsonObject contentText = postRenderer.getObject("contentText");
            if (contentText.isEmpty()) {
                return "";
            }
            return HtmlParser.htmlToString(Utils.removeUTF8BOM(
                    getTextFromObject(contentText, true)));
        } catch (final Exception e) {
            throw new ParsingException("Could not get post text", e);
        }
    }

    @Override
    public String getUploaderName() throws ParsingException {
        try {
            return getTextFromObject(postRenderer.getObject("authorText"));
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public String getUploaderUrl() {
        final String browseId = postRenderer.getObject("authorEndpoint")
                .getObject("browseEndpoint").getString("browseId");
        return Utils.isBlank(browseId) ? "" : YOUTUBE_URL + "/channel/" + browseId;
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        return imagesAt("authorThumbnail.thumbnails");
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        try {
            return getTextFromObject(postRenderer.getObject("publishedTimeText"));
        } catch (final Exception e) {
            return "";
        }
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        String textualDate = getTextualUploadDate();
        textualDate = textualDate.replaceAll("(?i)\\s*\\(edited\\)\\s*$", "").trim();
        return Utils.isBlank(textualDate) ? null : timeAgoParser.parse(textualDate);
    }

    @Override
    public long getLikeCount() throws ParsingException {
        return parseCount(getTextualLikeCount());
    }

    @Override
    public long getCommentCount() throws ParsingException {
        return parseCount(getTextualCommentCount());
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        return textAt("voteCount");
    }

    @Override
    public String getTextualCommentCount() throws ParsingException {
        String result = textAt("actionButtons.commentActionButtonsRenderer.replyButton"
                + ".buttonRenderer.text");
        if (Utils.isBlank(result)) {
            result = textAt("actionButtons.commentActionButtonsRenderer.replyButton"
                    + ".buttonRenderer.accessibility.label");
        }
        return result;
    }

    @Nonnull
    @Override
    public List<Image> getImages() throws ParsingException {
        final JsonObject attachment = postRenderer.getObject("backstageAttachment");
        if (attachment.has("postMultiImageRenderer")) {
            final List<Image> images = new ArrayList<>();
            for (final Object object : attachment.getObject("postMultiImageRenderer")
                    .getArray("images")) {
                final JsonObject image = ((JsonObject) object).getObject("backstageImageRenderer");
                images.addAll(imagesFromRenderer(image));
            }
            return images;
        }
        if (attachment.has("backstageImageRenderer")) {
            return imagesFromRenderer(attachment.getObject("backstageImageRenderer"));
        }
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public PostInfoItem.Attachment getAttachment() throws ParsingException {
        final JsonObject attachment = postRenderer.getObject("backstageAttachment");
        if (attachment.has("videoRenderer")) {
            final JsonObject video = attachment.getObject("videoRenderer");
            final String videoId = video.getString("videoId");
            return new PostInfoItem.Attachment(
                    PostInfoItem.AttachmentType.VIDEO,
                    Utils.isBlank(videoId) ? endpointUrl(video)
                            : YOUTUBE_URL + "/watch?v=" + videoId,
                    textFrom(video.getObject("title")),
                    textFrom(video.getObject("descriptionSnippet")),
                    imagesFrom(video.getObject("thumbnail").getArray("thumbnails")));
        }
        if (attachment.has("playlistRenderer")) {
            final JsonObject playlist = attachment.getObject("playlistRenderer");
            final String playlistId = playlist.getString("playlistId");
            return new PostInfoItem.Attachment(
                    PostInfoItem.AttachmentType.PLAYLIST,
                    Utils.isBlank(playlistId) ? endpointUrl(playlist)
                            : YOUTUBE_URL + "/playlist?list=" + playlistId,
                    textFrom(playlist.getObject("title")),
                    textFrom(playlist.getObject("videoCountText")),
                    imagesFrom(playlist.getObject("thumbnails").getArray("thumbnails")));
        }

        JsonObject link = attachment.getObject("linkPreviewRenderer");
        if (link.isEmpty()) {
            link = attachment.getObject("backstageLinkRenderer");
        }
        if (!link.isEmpty()) {
            String url = link.getString("targetUrl");
            if (Utils.isBlank(url)) {
                url = endpointUrl(link);
            }
            List<Image> thumbnails = imagesFrom(link.getObject("thumbnail")
                    .getArray("thumbnails"));
            if (thumbnails.isEmpty()) {
                thumbnails = imagesFrom(link.getObject("image").getArray("thumbnails"));
            }
            return new PostInfoItem.Attachment(
                    PostInfoItem.AttachmentType.LINK,
                    url,
                    textFrom(link.getObject("title")),
                    textFrom(link.getObject("description")),
                    thumbnails);
        }
        return null;
    }

    @Nullable
    @Override
    public PostInfoItem.Poll getPoll() throws ParsingException {
        final JsonObject attachment = postRenderer.getObject("backstageAttachment");
        JsonObject poll = attachment.getObject("pollRenderer");
        if (poll.isEmpty()) {
            poll = attachment.getObject("backstagePollRenderer");
        }
        if (poll.isEmpty()) {
            poll = postRenderer.getObject("pollRenderer");
        }
        if (poll.isEmpty()) {
            return null;
        }

        final List<PostInfoItem.PollChoice> choices = new ArrayList<>();
        for (final Object object : poll.getArray("choices")) {
            final JsonObject choice = (JsonObject) object;
            String percentage = textFrom(choice.getObject("votePercentage"));
            if (Utils.isBlank(percentage)) {
                percentage = choice.getString("votePercentage");
            }
            choices.add(new PostInfoItem.PollChoice(
                    textFrom(choice.getObject("text")), percentage));
        }
        final String totalVotes = textFrom(poll.getObject("totalVotes"));
        return choices.isEmpty() ? null : new PostInfoItem.Poll(choices, totalVotes);
    }

    @Override
    public boolean isPinned() {
        return postRenderer.has("pinnedPostBadge") || postRenderer.has("pinnedBadge");
    }

    @Override
    public boolean isEdited() throws ParsingException {
        return getTextualUploadDate().toLowerCase(Locale.ROOT).contains("edited");
    }

    @Nonnull
    private List<Image> imagesAt(@Nonnull final String path) throws ParsingException {
        try {
            return getImagesFromThumbnailsArray(JsonUtils.getArray(postRenderer, path));
        } catch (final Exception e) {
            throw new ParsingException("Could not get post images at " + path, e);
        }
    }

    @Nonnull
    private static List<Image> imagesFromRenderer(@Nonnull final JsonObject renderer)
            throws ParsingException {
        return imagesFrom(renderer.getObject("image").getArray("thumbnails"));
    }

    @Nonnull
    private static List<Image> imagesFrom(@Nonnull final JsonArray thumbnails)
            throws ParsingException {
        if (thumbnails.isEmpty()) {
            return Collections.emptyList();
        }
        return getImagesFromThumbnailsArray(thumbnails);
    }

    private String textAt(@Nonnull final String path) throws ParsingException {
        try {
            return textFrom(JsonUtils.getObject(postRenderer, path));
        } catch (final Exception e) {
            return "";
        }
    }

    private static String textFrom(@Nonnull final JsonObject textObject)
            throws ParsingException {
        return textObject.isEmpty() ? "" : getTextFromObject(textObject, true);
    }

    private static long parseCount(@Nullable final String text) throws ParsingException {
        if (Utils.isBlank(text)) {
            return PostInfoItem.UNKNOWN_COUNT;
        }
        try {
            return Utils.mixedNumberWordToLong(text);
        } catch (final Exception e) {
            final String digits = Utils.removeNonDigitCharacters(text);
            if (Utils.isBlank(digits)) {
                return PostInfoItem.UNKNOWN_COUNT;
            }
            try {
                return Long.parseLong(digits);
            } catch (final NumberFormatException numberFormatException) {
                throw new ParsingException("Could not parse post count", numberFormatException);
            }
        }
    }

    @Nonnull
    private static String endpointUrl(@Nonnull final JsonObject renderer) {
        final String url = renderer.getObject("navigationEndpoint")
                .getObject("commandMetadata").getObject("webCommandMetadata")
                .getString("url");
        return Utils.isBlank(url) ? "" : absoluteUrl(url);
    }

    @Nonnull
    private static String absoluteUrl(@Nonnull final String url) {
        return url.startsWith("/") ? YOUTUBE_URL + url : url;
    }
}
