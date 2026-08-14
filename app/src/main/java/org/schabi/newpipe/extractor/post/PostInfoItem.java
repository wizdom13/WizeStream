package org.schabi.newpipe.extractor.post;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A read-only channel post. Service-specific renderer data must be normalized into this model so
 * the Android UI does not depend on YouTube's response shape.
 */
public final class PostInfoItem extends InfoItem {
    public static final long UNKNOWN_COUNT = -1;

    private String postId = "";
    private String content = "";
    private String uploaderName = "";
    private String uploaderUrl = "";
    @Nonnull
    private List<Image> uploaderAvatars = Collections.emptyList();
    private String textualUploadDate = "";
    @Nullable
    private DateWrapper uploadDate;
    private long likeCount = UNKNOWN_COUNT;
    private long commentCount = UNKNOWN_COUNT;
    private String textualLikeCount = "";
    private String textualCommentCount = "";
    @Nonnull
    private List<Image> images = Collections.emptyList();
    @Nullable
    private Attachment attachment;
    @Nullable
    private Poll poll;
    private boolean pinned;
    private boolean edited;

    public PostInfoItem(final int serviceId, final String url, final String name) {
        super(InfoType.POST, serviceId, url, name);
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(final String postId) {
        this.postId = postId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(final String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public String getUploaderUrl() {
        return uploaderUrl;
    }

    public void setUploaderUrl(final String uploaderUrl) {
        this.uploaderUrl = uploaderUrl;
    }

    @Nonnull
    public List<Image> getUploaderAvatars() {
        return uploaderAvatars;
    }

    public void setUploaderAvatars(@Nonnull final List<Image> uploaderAvatars) {
        this.uploaderAvatars = Collections.unmodifiableList(new ArrayList<>(uploaderAvatars));
    }

    public String getTextualUploadDate() {
        return textualUploadDate;
    }

    public void setTextualUploadDate(final String textualUploadDate) {
        this.textualUploadDate = textualUploadDate;
    }

    @Nullable
    public DateWrapper getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(@Nullable final DateWrapper uploadDate) {
        this.uploadDate = uploadDate;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(final long likeCount) {
        this.likeCount = likeCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(final long commentCount) {
        this.commentCount = commentCount;
    }

    public String getTextualLikeCount() {
        return textualLikeCount;
    }

    public void setTextualLikeCount(final String textualLikeCount) {
        this.textualLikeCount = textualLikeCount;
    }

    public String getTextualCommentCount() {
        return textualCommentCount;
    }

    public void setTextualCommentCount(final String textualCommentCount) {
        this.textualCommentCount = textualCommentCount;
    }

    @Nonnull
    public List<Image> getImages() {
        return images;
    }

    public void setImages(@Nonnull final List<Image> images) {
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
    }

    @Nullable
    public Attachment getAttachment() {
        return attachment;
    }

    public void setAttachment(@Nullable final Attachment attachment) {
        this.attachment = attachment;
    }

    @Nullable
    public Poll getPoll() {
        return poll;
    }

    public void setPoll(@Nullable final Poll poll) {
        this.poll = poll;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(final boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(final boolean edited) {
        this.edited = edited;
    }

    public enum AttachmentType {
        VIDEO,
        PLAYLIST,
        LINK
    }

    public static final class Attachment implements Serializable {
        @Nonnull
        private final AttachmentType type;
        @Nonnull
        private final String url;
        @Nonnull
        private final String title;
        @Nonnull
        private final String description;
        @Nonnull
        private final List<Image> thumbnails;

        public Attachment(@Nonnull final AttachmentType type,
                          @Nonnull final String url,
                          @Nonnull final String title,
                          @Nonnull final String description,
                          @Nonnull final List<Image> thumbnails) {
            this.type = type;
            this.url = url;
            this.title = title;
            this.description = description;
            this.thumbnails = Collections.unmodifiableList(new ArrayList<>(thumbnails));
        }

        @Nonnull
        public AttachmentType getType() {
            return type;
        }

        @Nonnull
        public String getUrl() {
            return url;
        }

        @Nonnull
        public String getTitle() {
            return title;
        }

        @Nonnull
        public String getDescription() {
            return description;
        }

        @Nonnull
        public List<Image> getThumbnails() {
            return thumbnails;
        }
    }

    public static final class Poll implements Serializable {
        @Nonnull
        private final List<PollChoice> choices;
        @Nonnull
        private final String totalVotes;

        public Poll(@Nonnull final List<PollChoice> choices,
                    @Nonnull final String totalVotes) {
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
            this.totalVotes = totalVotes;
        }

        @Nonnull
        public List<PollChoice> getChoices() {
            return choices;
        }

        @Nonnull
        public String getTotalVotes() {
            return totalVotes;
        }
    }

    public static final class PollChoice implements Serializable {
        @Nonnull
        private final String text;
        @Nonnull
        private final String votePercentage;

        public PollChoice(@Nonnull final String text,
                          @Nonnull final String votePercentage) {
            this.text = text;
            this.votePercentage = votePercentage;
        }

        @Nonnull
        public String getText() {
            return text;
        }

        @Nonnull
        public String getVotePercentage() {
            return votePercentage;
        }
    }
}
