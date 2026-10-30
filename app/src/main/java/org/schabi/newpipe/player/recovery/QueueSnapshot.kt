/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.recovery

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

internal data class QueueEntry(
    val service: Int,
    val url: String,
    val title: String,
    val duration: Long,
    val uploader: String,
    val type: String,
    val local: Boolean,
    val mime: String?,
    val album: String?,
    val folder: String?,
    val localId: Long,
    val uploaderUrl: String,
    val shortForm: Boolean
) {
    fun item(): PlayQueueItem {
        val streamType = StreamType.valueOf(type)
        return if (local) {
            PlayQueueItem.localMedia(title, url, duration, uploader, album, folder, mime, localId, streamType == StreamType.VIDEO_STREAM, null)
        } else {
            PlayQueueItem(
                StreamInfoItem(service, url, title, streamType).also {
                    it.duration = duration
                    it.uploaderName = uploader
                    it.uploaderUrl = uploaderUrl
                    it.isShortFormContent = shortForm
                }
            )
        }
    }

    fun json(): JSONObject = JSONObject().put("service", service).put("url", url)
        .put("title", title).put("duration", duration).put("uploader", uploader)
        .put("type", type).put("local", local).put("mime", mime)
        .put("album", album).put("folder", folder).put("localId", localId)
        .put("uploaderUrl", uploaderUrl).put("shortForm", shortForm)

    companion object {
        fun from(item: PlayQueueItem): QueueEntry = QueueEntry(
            item.serviceId, item.url, item.title, item.duration, item.uploader,
            item.streamType.name, item.isLocalMedia, item.mimeType, item.album, item.folder, item.localMediaId,
            item.uploaderUrl.orEmpty(), item.isShortFormContent
        )

        fun parse(json: JSONObject): QueueEntry {
            val entry = QueueEntry(
                json.getInt("service"), json.getString("url"), json.getString("title"),
                json.getLong("duration"), json.getString("uploader"), json.getString("type"),
                json.getBoolean("local"), json.optString("mime").ifEmpty { null },
                json.optString("album").ifEmpty { null }, json.optString("folder").ifEmpty { null }, json.getLong("localId"),
                json.optString("uploaderUrl"), json.optBoolean("shortForm")
            )
            require(entry.url.isNotBlank() && entry.url.length <= 16384)
            require(entry.local || entry.service >= 0)
            StreamType.valueOf(entry.type)
            return entry
        }
    }
}

internal data class QueueSnapshot(val session: String, val savedAt: Long, val entries: List<QueueEntry>, val index: Int, val position: Long) {
    fun queue(): PlayQueue = LocalMediaPlayQueue(entries.map(QueueEntry::item), index, true).also {
        val type = StreamType.valueOf(entries[index].type)
        if (type == StreamType.VIDEO_STREAM || type == StreamType.AUDIO_STREAM) it.setRecovery(index, position)
    }

    fun json(): JSONObject = JSONObject().put("session", session).put("savedAt", savedAt)
        .put("index", index).put("position", position)
        .put("entries", JSONArray().also { list -> entries.forEach { list.put(it.json()) } })

    companion object {
        const val MAX_ITEMS = 1000
        fun capture(queue: PlayQueue, session: String, position: Long, now: Long): QueueSnapshot? {
            val items = queue.streams.toList()
            val index = queue.index
            if (items.isEmpty() || items.size > MAX_ITEMS || index !in items.indices) return null
            return QueueSnapshot(session, now, items.map(QueueEntry::from), index, position.coerceAtLeast(0))
        }

        fun parse(json: JSONObject): QueueSnapshot {
            val array = json.getJSONArray("entries")
            require(array.length() in 1..MAX_ITEMS)
            val entries = (0 until array.length()).map { QueueEntry.parse(array.getJSONObject(it)) }
            val index = json.getInt("index")
            require(index in entries.indices)
            return QueueSnapshot(json.getString("session"), json.getLong("savedAt"), entries, index, json.getLong("position").coerceAtLeast(0))
        }
    }
}
