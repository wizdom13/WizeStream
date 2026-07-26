/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import us.shandian.giga.get.MetadataOnlyFinishedMission

object SyncedDownloadMetadataRepository {
    @JvmStatic
    fun load(context: Context): List<MetadataOnlyFinishedMission> {
        return RoomStructuredPreferenceSyncStore.get(context)
            .getCompletedDownloadMetadata()
            .map { download ->
                MetadataOnlyFinishedMission(
                    download.syncId,
                    download.sourceUrl,
                    download.displayName,
                    download.mimeType,
                    download.sizeBytes,
                    download.completedAtEpochMillis,
                    download.mediaKind[0]
                )
            }
    }
}
