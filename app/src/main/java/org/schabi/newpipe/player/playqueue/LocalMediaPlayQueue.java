/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.playqueue;

import androidx.annotation.NonNull;

import java.util.List;

/** A complete queue made from device-local media, or a mixture of local and remote items. */
public final class LocalMediaPlayQueue extends PlayQueue {
    public LocalMediaPlayQueue(@NonNull final List<PlayQueueItem> items, final int index) {
        super(index, items);
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
