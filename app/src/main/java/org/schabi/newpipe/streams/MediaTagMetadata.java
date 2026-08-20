package org.schabi.newpipe.streams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.time.format.DateTimeFormatter;

/** Immutable metadata fields embedded into supported downloaded media containers. */
public final class MediaTagMetadata {
    @Nullable
    final String title;
    @Nullable
    final String artist;
    @Nullable
    final String genre;
    @Nullable
    final String date;
    @Nullable
    final String comment;

    public MediaTagMetadata(@Nullable final String title,
                            @Nullable final String artist,
                            @Nullable final String genre,
                            @Nullable final String date,
                            @Nullable final String comment) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.date = date;
        this.comment = comment;
    }

    @NonNull
    public static MediaTagMetadata from(@NonNull final StreamInfo streamInfo) {
        final String uploadDate = streamInfo.getUploadDate() == null
                ? null
                : streamInfo.getUploadDate().offsetDateTime().format(DateTimeFormatter.ISO_DATE);
        return new MediaTagMetadata(streamInfo.getName(), streamInfo.getUploaderName(),
                streamInfo.getCategory(), uploadDate, streamInfo.getUrl());
    }
}
