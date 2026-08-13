package org.schabi.newpipe.extractor.post;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface PostInfoItemExtractor extends InfoItemExtractor {
    default String getPostId() throws ParsingException {
        return "";
    }

    default String getContent() throws ParsingException {
        return "";
    }

    default String getUploaderName() throws ParsingException {
        return "";
    }

    default String getUploaderUrl() throws ParsingException {
        return "";
    }

    @Nonnull
    default List<Image> getUploaderAvatars() throws ParsingException {
        return Collections.emptyList();
    }

    default String getTextualUploadDate() throws ParsingException {
        return "";
    }

    @Nullable
    default DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

    default long getLikeCount() throws ParsingException {
        return PostInfoItem.UNKNOWN_COUNT;
    }

    default long getCommentCount() throws ParsingException {
        return PostInfoItem.UNKNOWN_COUNT;
    }

    default String getTextualLikeCount() throws ParsingException {
        return "";
    }

    default String getTextualCommentCount() throws ParsingException {
        return "";
    }

    @Nonnull
    default List<Image> getImages() throws ParsingException {
        return Collections.emptyList();
    }

    @Nullable
    default PostInfoItem.Attachment getAttachment() throws ParsingException {
        return null;
    }

    @Nullable
    default PostInfoItem.Poll getPoll() throws ParsingException {
        return null;
    }

    default boolean isPinned() throws ParsingException {
        return false;
    }

    default boolean isEdited() throws ParsingException {
        return false;
    }

    @Override
    default String getThumbnailUrl() throws ParsingException {
        final List<Image> images = getImages();
        if (!images.isEmpty()) {
            return images.get(0).getUrl();
        }
        final PostInfoItem.Attachment attachment = getAttachment();
        return attachment == null || attachment.getThumbnails().isEmpty()
                ? null : attachment.getThumbnails().get(0).getUrl();
    }
}
