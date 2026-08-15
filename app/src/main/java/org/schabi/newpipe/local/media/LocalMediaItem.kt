/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.media

import java.io.Serializable
import org.schabi.newpipe.player.playqueue.PlayQueueItem

data class LocalMediaItem(
    val mediaStoreId: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val mimeType: String,
    val durationSeconds: Long,
    val addedAtSeconds: Long,
    val isVideo: Boolean,
    val thumbnailUri: String? = contentUri
) : Serializable {
    fun toPlayQueueItem(): PlayQueueItem = PlayQueueItem.localMedia(
        title,
        contentUri,
        durationSeconds,
        artist,
        album,
        folder,
        mimeType,
        mediaStoreId,
        isVideo,
        thumbnailUri
    )
}
