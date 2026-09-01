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
    val thumbnailUri: String? = contentUri,
    val artistId: Long = -1L,
    val albumId: Long = -1L,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val relativePath: String = "",
    val volumeName: String = "",
    val sizeBytes: Long = 0L
) : Serializable {
    val stableId: String
        get() = contentUri

    val isAudio: Boolean
        get() = !isVideo

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
