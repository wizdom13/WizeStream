/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.schabi.newpipe.player.playqueue.PlayQueueItem

internal data class LocalMediaTechnicalMetadata(
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val capturedAtMillis: Long = 0,
    val audioSampleRate: Int = 0,
    val audioChannelCount: Int = 0,
    val audioBitrate: Int = 0
)

internal object LocalMediaMetadataReader {
    fun read(context: Context, item: PlayQueueItem): LocalMediaTechnicalMetadata {
        val uri = Uri.parse(item.url)
        val retrieverMetadata = readRetrieverMetadata(context, uri)
        val audioMetadata = readAudioMetadata(context, uri)
        return retrieverMetadata.copy(
            capturedAtMillis = readCapturedAtMillis(context, uri)
                .takeIf { it > 0 } ?: retrieverMetadata.capturedAtMillis,
            audioSampleRate = audioMetadata.audioSampleRate,
            audioChannelCount = audioMetadata.audioChannelCount,
            audioBitrate = audioMetadata.audioBitrate
        )
    }

    private fun readRetrieverMetadata(
        context: Context,
        uri: Uri
    ): LocalMediaTechnicalMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            LocalMediaTechnicalMetadata(
                width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                rotation = retriever.intMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                ),
                capturedAtMillis = parseMetadataDate(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                )
            )
        } catch (_: Exception) {
            LocalMediaTechnicalMetadata()
        } finally {
            retriever.release()
        }
    }

    private fun readAudioMetadata(context: Context, uri: Uri): LocalMediaTechnicalMetadata {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount)
                .asSequence()
                .map(extractor::getTrackFormat)
                .firstOrNull { format ->
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                ?.let { format ->
                    LocalMediaTechnicalMetadata(
                        audioSampleRate = format.intValue(MediaFormat.KEY_SAMPLE_RATE),
                        audioChannelCount = format.intValue(MediaFormat.KEY_CHANNEL_COUNT),
                        audioBitrate = format.intValue(MediaFormat.KEY_BIT_RATE)
                    )
                } ?: LocalMediaTechnicalMetadata()
        } catch (_: Exception) {
            LocalMediaTechnicalMetadata()
        } finally {
            extractor.release()
        }
    }

    private fun readCapturedAtMillis(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(
            uri,
            arrayOf(DATE_TAKEN_COLUMN),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int = extractMetadata(key)
        ?.toIntOrNull() ?: 0

    private fun MediaFormat.intValue(key: String): Int = if (containsKey(key)) {
        getInteger(key)
    } else {
        0
    }

    private fun parseMetadataDate(value: String?): Long {
        val compactDate = value?.take(8)?.takeIf { date -> date.all(Char::isDigit) }
            ?: return 0L
        return try {
            LocalDate.parse(compactDate, DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private const val DATE_TAKEN_COLUMN = "datetaken"
}

internal object LocalMediaMetadataFormatter {
    fun format(mimeType: String?): String = when (mimeType?.lowercase(Locale.ROOT)) {
        "video/mp4" -> "MP4"
        "video/x-matroska" -> "MKV"
        "video/webm" -> "WebM"
        "audio/mpeg" -> "MP3"
        "audio/mp4", "audio/x-m4a" -> "M4A"
        "audio/flac" -> "FLAC"
        "audio/ogg", "application/ogg" -> "OGG"
        "audio/wav", "audio/x-wav" -> "WAV"
        else -> mimeType?.substringAfter('/')?.uppercase().orEmpty()
    }

    fun resolution(metadata: LocalMediaTechnicalMetadata): String {
        if (metadata.width <= 0 || metadata.height <= 0) return ""
        val rotated = metadata.rotation == 90 || metadata.rotation == 270
        val width = if (rotated) metadata.height else metadata.width
        val height = if (rotated) metadata.width else metadata.height
        return "$width × $height"
    }
}
