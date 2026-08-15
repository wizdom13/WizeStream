package org.schabi.newpipe.player.playqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PlayQueueItem implements Serializable {
    public static final long RECOVERY_UNSET = Long.MIN_VALUE;
    public static final int LOCAL_SERVICE_ID = -1;
    private static final String EMPTY_STRING = "";

    public enum SourceType {
        REMOTE,
        LOCAL
    }

    @NonNull
    private final String title;
    @NonNull
    private final String url;
    private final int serviceId;
    private final long duration;
    @NonNull
    private final List<Image> thumbnailImages;
    @NonNull
    private final String uploader;
    private final String uploaderUrl;
    @NonNull
    private final StreamType streamType;
    @NonNull
    private final SourceType sourceType;
    @Nullable
    private final String mimeType;
    @Nullable
    private final String album;
    @Nullable
    private final String folder;
    private final long localMediaId;
    @Nullable
    private final String localThumbnailUrl;

    private boolean isAutoQueued;

    private long recoveryPosition;
    private Throwable error;

    public PlayQueueItem(@NonNull final StreamInfo info) {
        this(info.getName(), info.getUrl(), info.getServiceId(), info.getDuration(),
                ExtractorImageCompat.thumbnailImages(info), info.getUploaderName(),
                info.getUploaderUrl(), info.getStreamType(), SourceType.REMOTE,
                null, null, null, -1L, null);

        if (info.getStartPosition() > 0) {
            setRecoveryPosition(info.getStartPosition() * 1000);
        }
    }

    public PlayQueueItem(@NonNull final StreamInfoItem item) {
        this(item.getName(), item.getUrl(), item.getServiceId(), item.getDuration(),
                ExtractorImageCompat.thumbnailImages(item), item.getUploaderName(),
                item.getUploaderUrl(), item.getStreamType(), SourceType.REMOTE,
                null, null, null, -1L, null);
    }

    @SuppressWarnings("ParameterNumber")
    public static PlayQueueItem localMedia(@NonNull final String title,
                                           @NonNull final String contentUri,
                                           final long duration,
                                           @Nullable final String artist,
                                           @Nullable final String album,
                                           @Nullable final String folder,
                                           @Nullable final String mimeType,
                                           final long localMediaId,
                                           final boolean video,
                                           @Nullable final String thumbnailUri) {
        return new PlayQueueItem(title, contentUri, LOCAL_SERVICE_ID, duration,
                Collections.emptyList(), artist, null,
                video ? StreamType.VIDEO_STREAM : StreamType.AUDIO_STREAM,
                SourceType.LOCAL, mimeType, album, folder, localMediaId, thumbnailUri);
    }

    @SuppressWarnings("ParameterNumber")
    private PlayQueueItem(@Nullable final String name, @Nullable final String url,
                          final int serviceId, final long duration,
                          final List<Image> thumbnails, @Nullable final String uploader,
                          final String uploaderUrl, @NonNull final StreamType streamType,
                          @NonNull final SourceType sourceType, @Nullable final String mimeType,
                          @Nullable final String album, @Nullable final String folder,
                          final long localMediaId, @Nullable final String localThumbnailUrl) {
        this.title = name != null ? name : EMPTY_STRING;
        this.url = url != null ? url : EMPTY_STRING;
        this.serviceId = serviceId;
        this.duration = duration;
        this.thumbnailImages = thumbnails;
        this.uploader = uploader != null ? uploader : EMPTY_STRING;
        this.uploaderUrl = uploaderUrl;
        this.streamType = streamType;
        this.sourceType = sourceType;
        this.mimeType = mimeType;
        this.album = album;
        this.folder = folder;
        this.localMediaId = localMediaId;
        this.localThumbnailUrl = localThumbnailUrl;

        this.recoveryPosition = RECOVERY_UNSET;
    }

    /** Whether these two items should be treated as the same stream
     * for the sake of keeping the same player running when e.g. jumping between timestamps.
     *
     * @param other the {@link PlayQueueItem} to compare against.
     * @return whether the two items are the same so the stream can be re-used.
     */
    public boolean isSameItem(@Nullable final PlayQueueItem other) {
        if (other == null) {
            return false;
        }
        // We assume that the same service & URL uniquely determines
        // that we can keep the same stream running.
        return sourceType == other.sourceType
                && serviceId == other.serviceId
                && url.equals(other.url);
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public int getServiceId() {
        return serviceId;
    }

    public long getDuration() {
        return duration;
    }

    @NonNull
    public List<Image> getThumbnails() {
        return thumbnailImages;
    }

    @NonNull
    public String getUploader() {
        return uploader;
    }

    public String getUploaderUrl() {
        return uploaderUrl;
    }

    @NonNull
    public StreamType getStreamType() {
        return streamType;
    }

    @NonNull
    public SourceType getSourceType() {
        return sourceType;
    }

    public boolean isLocalMedia() {
        return sourceType == SourceType.LOCAL;
    }

    @Nullable
    public String getMimeType() {
        return mimeType;
    }

    @Nullable
    public String getAlbum() {
        return album;
    }

    @Nullable
    public String getFolder() {
        return folder;
    }

    public long getLocalMediaId() {
        return localMediaId;
    }

    @Nullable
    public String getLocalThumbnailUrl() {
        return localThumbnailUrl;
    }

    public long getRecoveryPosition() {
        return recoveryPosition;
    }

    /*package-private*/ void setRecoveryPosition(final long recoveryPosition) {
        this.recoveryPosition = recoveryPosition;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    @NonNull
    public Single<StreamInfo> getStream() {
        if (isLocalMedia()) {
            return Single.error(new IllegalStateException(
                    "Local media must be resolved without the online extractor"));
        }
        return ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> error = throwable);
    }

    public boolean isAutoQueued() {
        return isAutoQueued;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Item States, keep external access out
    ////////////////////////////////////////////////////////////////////////////

    public void setAutoQueued(final boolean autoQueued) {
        isAutoQueued = autoQueued;
    }
}
