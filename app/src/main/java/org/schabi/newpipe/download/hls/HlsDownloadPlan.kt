/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import java.io.IOException
import java.io.Serializable

/** Plain serialized values only: active Media3 resources must never enter mission metadata. */
data class HlsDownloadPlan(
    val cacheId: String,
    val manifestUrl: String,
    val requestHeaders: HashMap<String, String>,
    val streamKeys: ArrayList<HlsDownloadStreamKey>,
    val videoPlaylistUrl: String,
    val audioPlaylistUrl: String?,
    val height: Int,
    val durationUs: Long,
    val codecs: String?,
    val hasAudio: Boolean? = null
) : Serializable {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(manifestUrl)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .setStreamKeys(streamKeys.map { StreamKey(it.group, it.track) })
        .build()

    companion object {
        private const val serialVersionUID = 1L
    }
}

data class HlsDownloadStreamKey(val group: Int, val track: Int) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

class UnsupportedHlsDownloadException(message: String) : IOException(message)
