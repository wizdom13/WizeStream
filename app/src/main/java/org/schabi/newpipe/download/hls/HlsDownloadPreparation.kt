/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.URL

/** Reads complete bounded playlists into cache before permitting any segment download. */
object HlsDownloadPreparation {
    internal const val MAX_MANIFEST_BYTES = 1024 * 1024
    internal const val MAX_SEGMENTS = 20_000
    internal const val MAX_DURATION_US = 24L * 60 * 60 * 1_000_000

    fun prepare(
        cache: Cache,
        cacheId: String,
        manifestUrl: String,
        requestHeaders: Map<String, String>,
        desiredHeight: Int? = null,
        upstreamFactory: DataSource.Factory
    ): HlsDownloadPlan {
        validateUrl(manifestUrl)
        val factory = HlsDownloadCache.networkFactory(cache, upstreamFactory)
        val first = readPlaylist(factory, manifestUrl, HlsPlaylistParser())
        if (first is HlsMediaPlaylist) {
            validateMedia(first)
            return HlsDownloadPlan(
                cacheId, manifestUrl, HashMap(requestHeaders), ArrayList(), manifestUrl,
                null, desiredHeight ?: Format.NO_VALUE, first.durationUs, null
            )
        }
        val master = first as? HlsMultivariantPlaylist
            ?: unsupported("This HLS playlist cannot be downloaded.")
        if (master.sessionKeyDrmInitData.isNotEmpty()) unsupported("Encrypted HLS downloads are not supported.")
        if (master.variants.size > 200 || master.audios.size > 200) unsupported("This HLS playlist contains too many tracks.")
        val candidates = master.variants.withIndex().filter { (_, variant) ->
            variant.videoGroupId == null && supportedCodecs(variant.format.codecs, requireVideo = true)
        }
        if (candidates.isEmpty()) unsupported("HLS downloads currently support AVC video and AAC audio only.")
        val eligible = desiredHeight?.let { maximum ->
            candidates.filter { it.value.format.height in 1..maximum }.ifEmpty { candidates }
        } ?: candidates
        val selected = eligible.maxWithOrNull(
            compareBy<IndexedValue<HlsMultivariantPlaylist.Variant>> { it.value.format.height }
                .thenBy { it.value.format.bitrate }
        ) ?: unsupported("No supported HLS video track was found.")
        val variant = selected.value
        validateUrl(variant.url.toString())
        val keys = arrayListOf(HlsDownloadStreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, selected.index))
        val parser = HlsPlaylistParser(master, null)
        val video = readPlaylist(factory, variant.url.toString(), parser) as? HlsMediaPlaylist
            ?: unsupported("Nested HLS master playlists are not supported.")
        validateMedia(video)
        val audioGroup = variant.audioGroupId
        val audios = master.audios.withIndex().filter { it.value.groupId == audioGroup }
        val selectedAudio = if (audioGroup == null) {
            null
        } else {
            audios.filter { supportedCodecs(it.value.format.codecs, requireVideo = false) }
                .maxWithOrNull(
                    compareBy<IndexedValue<HlsMultivariantPlaylist.Rendition>> {
                        it.value.format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                    }.thenBy { it.value.format.selectionFlags and C.SELECTION_FLAG_AUTOSELECT != 0 }
                )
        }
        if (audioGroup != null && audios.isNotEmpty() && selectedAudio == null) {
            unsupported("The selected HLS audio track is not supported.")
        }
        if (audioGroup != null && audios.isEmpty() && master.muxedAudioFormat == null) {
            unsupported("The selected HLS video is missing its audio track.")
        }
        val audioUrl = selectedAudio?.value?.url?.toString()
        if (audioUrl != null) {
            validateUrl(audioUrl)
            val audio = readPlaylist(factory, audioUrl, parser) as? HlsMediaPlaylist
                ?: unsupported("The selected HLS audio playlist is invalid.")
            validateMedia(audio)
            if (kotlin.math.abs(video.durationUs - audio.durationUs) > 2_000_000) {
                unsupported("The HLS audio and video durations do not match.")
            }
            keys.add(HlsDownloadStreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, requireNotNull(selectedAudio).index))
        }
        return HlsDownloadPlan(
            cacheId, manifestUrl, HashMap(requestHeaders), keys, variant.url.toString(), audioUrl,
            variant.format.height, video.durationUs, variant.format.codecs,
            when {
                selectedAudio != null || master.muxedAudioFormat != null -> true
                !variant.format.codecs.isNullOrBlank() -> variant.format.codecs.orEmpty().contains("mp4a.", ignoreCase = true)
                else -> null
            }
        )
    }

    internal fun desiredHeight(resolution: String?): Int? = resolution?.let {
        Regex("^(\\d{2,4})[pP](?:\\d{1,3})?$").matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull()
    }

    internal fun supportedCodecs(codecs: String?, requireVideo: Boolean): Boolean {
        if (codecs.isNullOrBlank()) return true // Actual tracks are checked before the MP4 is published.
        val tracks = codecs.split(',').map { it.trim().lowercase() }
        val video = tracks.any { it.startsWith("avc1.") || it.startsWith("avc3.") }
        return (!requireVideo || video) && tracks.all {
            it.startsWith("avc1.") || it.startsWith("avc3.") || it == "mp4a.40.2" || it == "mp4a.40.5" || it == "mp4a.40.29"
        }
    }

    private fun readPlaylist(factory: DataSource.Factory, url: String, parser: HlsPlaylistParser): HlsPlaylist {
        val source = factory.createDataSource()
        try {
            val length = source.open(DataSpec(Uri.parse(url)))
            if (length > MAX_MANIFEST_BYTES) unsupported("The HLS playlist is too large.")
            val finalUri = source.uri ?: Uri.parse(url)
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("HLS preparation was cancelled.")
                val count = source.read(buffer, 0, buffer.size)
                if (count == C.RESULT_END_OF_INPUT) break
                if (bytes.size() + count > MAX_MANIFEST_BYTES) unsupported("The HLS playlist is too large.")
                bytes.write(buffer, 0, count)
            }
            val body = bytes.toByteArray()
            validateTags(body.toString(Charsets.UTF_8))
            return parser.parse(finalUri, ByteArrayInputStream(body))
        } finally {
            source.close()
        }
    }

    internal fun validateTags(body: String) {
        body.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXT-X-SESSION-KEY:") ||
                line.startsWith("#EXT-X-KEY:") && !Regex("(?:^|,)METHOD=NONE(?:,|$)").containsMatchIn(line.substringAfter(':'))
            ) {
                unsupported("Encrypted HLS downloads are not supported.")
            }
            if (line == "#EXT-X-DISCONTINUITY" || line.startsWith("#EXT-X-GAP") || line.startsWith("#EXT-X-I-FRAMES-ONLY")) {
                unsupported("HLS playlists with gaps or changing tracks are not supported.")
            }
            if (line.startsWith("#EXT-X-CONTENT-STEERING:")) unsupported("HLS content steering is not supported for downloads.")
            if (line.startsWith("#EXT-X-START:")) {
                val offset = Regex("(?:^|,)TIME-OFFSET=([^,]+)").find(line.substringAfter(':'))?.groupValues?.get(1)?.toDoubleOrNull()
                if (offset == null || offset != 0.0) unsupported("HLS playlists with a playback start offset are not supported for downloads.")
            }
        }
    }

    internal fun validateMedia(playlist: HlsMediaPlaylist) {
        if (!playlist.hasEndTag) unsupported("Only completed HLS videos can be downloaded; live streams are not supported.")
        if (playlist.protectionSchemes != null || playlist.segments.any {
                it.fullSegmentEncryptionKeyUri != null || it.drmInitData != null ||
                    it.initializationSegment?.fullSegmentEncryptionKeyUri != null || it.initializationSegment?.drmInitData != null
            }
        ) {
            unsupported("Encrypted HLS downloads are not supported.")
        }
        if (playlist.segments.isEmpty() || playlist.segments.size > MAX_SEGMENTS || playlist.durationUs <= 0 || playlist.durationUs > MAX_DURATION_US) {
            unsupported("This HLS video is empty or exceeds the supported download limits.")
        }
        if (playlist.trailingParts.isNotEmpty() || playlist.interstitials.isNotEmpty() || playlist.segments.any {
                it.hasGapTag || it.relativeDiscontinuitySequence != 0 || it.durationUs <= 0
            }
        ) {
            unsupported("HLS playlists with gaps or changing tracks are not supported.")
        }
        val initialization = playlist.segments.map { it.initializationSegment?.let { init -> Triple(init.url, init.byteRangeOffset, init.byteRangeLength) } }.distinct()
        if (initialization.size > 1) unsupported("HLS videos with changing initialization segments are not supported.")
        playlist.segments.forEach { segment ->
            validateUrl(androidx.media3.common.util.UriUtil.resolve(playlist.baseUri, segment.url))
            segment.initializationSegment?.let { validateUrl(androidx.media3.common.util.UriUtil.resolve(playlist.baseUri, it.url)) }
        }
    }

    internal fun validateUrl(value: String): URL {
        val url = try {
            URL(value)
        } catch (_: Exception) {
            unsupported("The HLS resource URL is invalid.")
        }
        if (url.protocol !in setOf("http", "https") || url.host.isBlank() || url.userInfo != null || url.ref != null) {
            unsupported("HLS downloads require an HTTP or HTTPS resource URL.")
        }
        return url
    }

    private fun unsupported(message: String): Nothing = throw UnsupportedHlsDownloadException(message)
}
