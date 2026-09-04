/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.WorkerThread
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.schabi.newpipe.player.playqueue.PlayQueueItem

data class LocalMediaEmbeddedMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Long = 0L
)

/** Resolves embedded tags for only the local item being played and caches the result by URI. */
object LocalMediaMetadataLoader {
    private const val CACHE_ENTRY_COUNT = 128

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LinkedHashMap<String, LocalMediaEmbeddedMetadata>(
        CACHE_ENTRY_COUNT,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, LocalMediaEmbeddedMetadata>?
        ): Boolean = size > CACHE_ENTRY_COUNT
    }

    fun interface MetadataCallback {
        fun onLoaded(metadata: LocalMediaEmbeddedMetadata)
    }

    /** The callback is always dispatched on the main thread. */
    fun load(
        context: Context,
        item: PlayQueueItem,
        callback: MetadataCallback
    ): Future<*> = executor.submit {
        val cached = synchronized(cache) { cache[item.url] }
        val metadata = cached ?: read(
            context.applicationContext.contentResolver,
            item.url,
            item.mimeType
        ).also { resolved ->
            synchronized(cache) { cache[item.url] = resolved }
        }
        if (!Thread.currentThread().isInterrupted) {
            mainHandler.post { callback.onLoaded(metadata) }
        }
    }

    @WorkerThread
    private fun read(
        resolver: ContentResolver,
        contentUri: String,
        mimeType: String?
    ): LocalMediaEmbeddedMetadata {
        val retrieverMetadata = readRetrieverMetadata(resolver, contentUri)
        if (!isOggContainer(mimeType, contentUri)) return retrieverMetadata

        val oggMetadata = try {
            resolver.openInputStream(Uri.parse(contentUri))?.use(OggVorbisCommentReader::read)
        } catch (_: Exception) {
            null
        }
        return LocalMediaEmbeddedMetadata(
            title = oggMetadata?.title ?: retrieverMetadata.title,
            artist = oggMetadata?.artist ?: retrieverMetadata.artist,
            album = oggMetadata?.album ?: retrieverMetadata.album,
            durationSeconds = retrieverMetadata.durationSeconds
        )
    }

    @WorkerThread
    private fun readRetrieverMetadata(
        resolver: ContentResolver,
        contentUri: String
    ): LocalMediaEmbeddedMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openFileDescriptor(Uri.parse(contentUri), "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                LocalMediaEmbeddedMetadata(
                    title = cleanTag(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ),
                    artist = cleanTag(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ),
                    album = cleanTag(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ),
                    durationSeconds = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0L }
                        ?.div(1_000L)
                        ?: 0L
                )
            } ?: LocalMediaEmbeddedMetadata()
        } catch (_: Exception) {
            LocalMediaEmbeddedMetadata()
        } finally {
            retriever.release()
        }
    }

    private fun isOggContainer(mimeType: String?, contentUri: String): Boolean {
        val normalizedMimeType = mimeType?.lowercase(Locale.ROOT)
        val normalizedUri = contentUri.lowercase(Locale.ROOT)
        return normalizedMimeType == "audio/ogg" ||
            normalizedMimeType == "application/ogg" ||
            normalizedMimeType == "audio/opus" ||
            normalizedUri.endsWith(".ogg") ||
            normalizedUri.endsWith(".oga") ||
            normalizedUri.endsWith(".opus")
    }
}

internal object OggVorbisCommentReader {
    private const val PAGE_HEADER_SIZE = 27
    private const val MAX_SCANNED_BYTES = 8 * 1024 * 1024
    private const val MAX_PACKET_BYTES = 8 * 1024 * 1024
    private const val MAX_COMMENT_COUNT = 4_096
    private const val MAX_FIELD_BYTES = 2 * 1024 * 1024
    private val vorbisCommentHeader = byteArrayOf(
        3,
        'v'.code.toByte(),
        'o'.code.toByte(),
        'r'.code.toByte(),
        'b'.code.toByte(),
        'i'.code.toByte(),
        's'.code.toByte()
    )
    private val opusCommentHeader = "OpusTags".toByteArray(StandardCharsets.US_ASCII)

    fun read(input: InputStream): LocalMediaEmbeddedMetadata? {
        val packet = ByteArrayOutputStream()
        var packetOverflowed = false
        var scannedBytes = 0

        while (scannedBytes < MAX_SCANNED_BYTES) {
            val header = ByteArray(PAGE_HEADER_SIZE)
            if (!input.readFully(header)) return null
            scannedBytes += header.size
            if (!header.copyOfRange(0, 4).contentEquals("OggS".toByteArray())) return null

            val segmentCount = header[26].toInt() and 0xff
            val lacingValues = ByteArray(segmentCount)
            if (!input.readFully(lacingValues)) return null
            scannedBytes += segmentCount

            lacingValues.forEach { lacingValue ->
                val segmentSize = lacingValue.toInt() and 0xff
                val segment = ByteArray(segmentSize)
                if (!input.readFully(segment)) return null
                scannedBytes += segmentSize

                if (!packetOverflowed && packet.size() + segmentSize <= MAX_PACKET_BYTES) {
                    packet.write(segment)
                } else {
                    packetOverflowed = true
                }

                if (segmentSize < 255) {
                    if (!packetOverflowed) {
                        parseCommentPacket(packet.toByteArray())?.let { return it }
                    }
                    packet.reset()
                    packetOverflowed = false
                }
            }
        }
        return null
    }

    private fun parseCommentPacket(packet: ByteArray): LocalMediaEmbeddedMetadata? {
        val headerSize = when {
            packet.startsWith(vorbisCommentHeader) -> vorbisCommentHeader.size
            packet.startsWith(opusCommentHeader) -> opusCommentHeader.size
            else -> return null
        }
        val reader = LittleEndianPacketReader(packet, headerSize)
        val vendorLength = reader.readLength(MAX_FIELD_BYTES) ?: return null
        if (!reader.skip(vendorLength)) return null
        val commentCount = reader.readUnsignedInt()
            ?.takeIf { it <= MAX_COMMENT_COUNT }
            ?: return null

        var title: String? = null
        var artist: String? = null
        var albumArtist: String? = null
        var album: String? = null
        repeat(commentCount) {
            val fieldLength = reader.readLength(MAX_FIELD_BYTES) ?: return null
            val field = reader.readString(fieldLength) ?: return null
            val separator = field.indexOf('=')
            if (separator <= 0) return@repeat
            val key = field.substring(0, separator).uppercase(Locale.ROOT)
            val value = cleanTag(field.substring(separator + 1)) ?: return@repeat
            when (key) {
                "TITLE" -> if (title == null) title = value
                "ARTIST" -> if (artist == null) artist = value
                "ALBUMARTIST" -> if (albumArtist == null) albumArtist = value
                "ALBUM" -> if (album == null) album = value
            }
        }
        if (title == null && artist == null && albumArtist == null && album == null) return null
        return LocalMediaEmbeddedMetadata(title, artist ?: albumArtist, album)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size &&
        prefix.indices.all { this[it] == prefix[it] }

    private fun InputStream.readFully(target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            if (read < 0) return false
            if (read == 0) continue
            offset += read
        }
        return true
    }

    private class LittleEndianPacketReader(
        private val data: ByteArray,
        private var position: Int
    ) {
        fun readUnsignedInt(): Int? {
            if (position > data.size - Integer.BYTES) return null
            val value = (data[position].toInt() and 0xff).toLong() or
                ((data[position + 1].toInt() and 0xff).toLong() shl 8) or
                ((data[position + 2].toInt() and 0xff).toLong() shl 16) or
                ((data[position + 3].toInt() and 0xff).toLong() shl 24)
            position += Integer.BYTES
            return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
        }

        fun readLength(maximum: Int): Int? = readUnsignedInt()?.takeIf { it <= maximum }

        fun skip(length: Int): Boolean {
            if (length < 0 || position > data.size - length) return false
            position += length
            return true
        }

        fun readString(length: Int): String? {
            if (length < 0 || position > data.size - length) return null
            return String(data, position, length, StandardCharsets.UTF_8).also {
                position += length
            }
        }
    }
}

private fun cleanTag(value: String?): String? = value
    ?.trim { it.isWhitespace() || it == '\u0000' }
    ?.takeIf(String::isNotEmpty)
