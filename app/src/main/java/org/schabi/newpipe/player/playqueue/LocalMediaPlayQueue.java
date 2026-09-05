/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.playqueue;

import androidx.annotation.NonNull;

import java.util.List;

/** A complete queue made from device-local media, or a mixture of local and remote items. */
public final class LocalMediaPlayQueue extends PlayQueue {
    private boolean openQueueOnStart;

    public LocalMediaPlayQueue(@NonNull final List<PlayQueueItem> items, final int index) {
        this(items, index, false);
    }

    public LocalMediaPlayQueue(@NonNull final List<PlayQueueItem> items,
                               final int index,
                               final boolean openQueueOnStart) {
        super(index, items);
        this.openQueueOnStart = openQueueOnStart;
    }

    /** Request the Play queue screen for the next main-player start. */
    public void requestOpenQueueOnStart() {
        openQueueOnStart = true;
    }

    /** @return whether this queue contains at least one device-local item. */
    public boolean containsLocalMedia() {
        return getStreams().stream().anyMatch(PlayQueueItem::isLocalMedia);
    }

    /**
     * Consumes the one-shot request to show the queue after playback starts.
     *
     * @return whether the queue screen should be opened for this playback request
     */
    public boolean consumeOpenQueueOnStart() {
        final boolean shouldOpen = openQueueOnStart;
        openQueueOnStart = false;
        return shouldOpen;
    }

    @Override
    public synchronized void shuffleFromStart() {
        if (containsLocalMedia()) {
            requestOpenQueueOnStart();
        }
        super.shuffleFromStart();
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch() {
        // Every item is already present.
    }
}
