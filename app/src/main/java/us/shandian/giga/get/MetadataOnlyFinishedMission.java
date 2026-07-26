/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.get;

import org.schabi.newpipe.streams.io.StoredFileHelper;

/**
 * Download metadata synchronized from another device without a local media file.
 */
public final class MetadataOnlyFinishedMission extends FinishedMission {
    public MetadataOnlyFinishedMission(
            final String syncId,
            final String sourceUrl,
            final String displayName,
            final String mimeType,
            final long sizeBytes,
            final long completedAtEpochMillis,
            final char mediaKind
    ) {
        this.syncId = syncId;
        source = sourceUrl;
        this.displayName = displayName;
        this.mimeType = mimeType;
        length = sizeBytes;
        timestamp = completedAtEpochMillis;
        kind = mediaKind;
        storage = new StoredFileHelper(null, displayName, mimeType, "");
    }
}
