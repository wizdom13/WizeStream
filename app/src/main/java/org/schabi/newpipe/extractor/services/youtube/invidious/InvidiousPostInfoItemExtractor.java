package org.schabi.newpipe.extractor.services.youtube.invidious;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.post.PostInfoItemExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class InvidiousPostInfoItemExtractor implements PostInfoItemExtractor {
    private final JsonObject post;
    private final JsonObject attachment;
    private final InvidiousApi api;

    InvidiousPostInfoItemExtractor(final JsonObject post, final InvidiousApi api) {
        this.post = post;
        this.attachment = post.getObject("attachment", new JsonObject());
        this.api = api;
    }

    @Override
    public String getPostId() {
        return post.getString("commentId", "");
    }

    @Override
    public String getUrl() {
        return "https://www.youtube.com/post/" + getPostId();
    }

    @Override
    public String getName() {
        return getContent();
    }

    @Override
    public String getContent() {
        return post.getString("content", "");
    }

    @Override
    public String getUploaderName() {
        return post.getString("author", "");
    }

    @Override
    public String getUploaderUrl() {
        return InvidiousApi.channelUrl(post);
    }

    @Override
    public List<Image> getUploaderAvatars() {
        return api.images(post.getArray("authorThumbnails"));
    }

    @Override
    public String getTextualUploadDate() {
        return post.getString("publishedText", "");
    }

    @Override
    public DateWrapper getUploadDate() {
        return InvidiousApi.date(post);
    }

    @Override
    public long getLikeCount() {
        return post.getLong("likeCount", PostInfoItem.UNKNOWN_COUNT);
    }

    @Override
    public long getCommentCount() {
        return post.getLong("replyCount", PostInfoItem.UNKNOWN_COUNT);
    }

    @Override
    public boolean isEdited() {
        return post.getBoolean("isEdited");
    }

    @Override
    public List<Image> getImages() {
        final List<Image> images = new ArrayList<>();
        if ("image".equals(attachment.getString("type"))) {
            addLargestImage(images, attachment.getArray("imageThumbnails"));
        } else if ("multiImage".equals(attachment.getString("type"))) {
            for (final Object image : attachment.getArray("images", new JsonArray())) {
                if (image instanceof JsonArray) {
                    addLargestImage(images, (JsonArray) image);
                }
            }
        }
        return images;
    }

    private void addLargestImage(final List<Image> images, final JsonArray thumbnails) {
        // Each thumbnail array represents resolutions of one image, not separate photos.
        api.images(thumbnails).stream().max(Comparator.comparingInt(Image::getHeight))
                .ifPresent(images::add);
    }

    @Override
    public PostInfoItem.Attachment getAttachment() {
        if (attachment.has("videoId")) {
            return new PostInfoItem.Attachment(PostInfoItem.AttachmentType.VIDEO,
                    "https://www.youtube.com/watch?v=" + attachment.getString("videoId"),
                    attachment.getString("title", ""), attachment.getString("description", ""),
                    api.images(attachment.getArray("videoThumbnails")));
        }
        if (attachment.has("playlistId")) {
            final String thumbnail = api.image(attachment.getString("playlistThumbnail", ""));
            return new PostInfoItem.Attachment(PostInfoItem.AttachmentType.PLAYLIST,
                    "https://www.youtube.com/playlist?list=" + attachment.getString("playlistId"),
                    attachment.getString("title", ""), attachment.getString("description", ""),
                    thumbnail.isEmpty() ? Collections.emptyList() : Collections.singletonList(
                            new Image(thumbnail, -1, -1, Image.ResolutionLevel.UNKNOWN)));
        }
        return null;
    }

    @Override
    public PostInfoItem.Poll getPoll() {
        if (!"poll".equals(attachment.getString("type"))) {
            return null;
        }
        final List<PostInfoItem.PollChoice> choices = new ArrayList<>();
        for (final Object entry : attachment.getArray("choices", new JsonArray())) {
            if (entry instanceof JsonObject choice) {
                choices.add(new PostInfoItem.PollChoice(choice.getString("text", ""), "",
                        api.images(choice.getArray("image"))));
            }
        }
        final long totalVotes = attachment.getLong("totalVotes", -1);
        return new PostInfoItem.Poll(choices, totalVotes < 0 ? "" : Long.toString(totalVotes));
    }
}
