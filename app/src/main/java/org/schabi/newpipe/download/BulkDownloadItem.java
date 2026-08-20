package org.schabi.newpipe.download;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.Serializable;

/** Lightweight, serializable reference to a stream that can be queued for download. */
public final class BulkDownloadItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int serviceId;
    @NonNull
    private final String url;
    @NonNull
    private final String title;

    public BulkDownloadItem(final int serviceId,
                            @NonNull final String url,
                            @NonNull final String title) {
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
    }

    @NonNull
    public static BulkDownloadItem from(@NonNull final StreamInfoItem item) {
        return new BulkDownloadItem(item.getServiceId(), item.getUrl(), item.getName());
    }

    @NonNull
    public static BulkDownloadItem from(@NonNull final PlayQueueItem item) {
        return new BulkDownloadItem(item.getServiceId(), item.getUrl(), item.getTitle());
    }

    public int getServiceId() {
        return serviceId;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public String getTitle() {
        return title;
    }
}
