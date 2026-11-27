/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.metrics.LogSessionId
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/** Remuxes completed private HLS input without a network upstream or an encoder. */
object HlsMp4Exporter {
    fun export(context: Context, plan: HlsDownloadPlan, output: File, isCancelled: () -> Boolean = { false }) {
        var complete = false
        try {
            checkCancelled(isCancelled)
            if (output.exists() && !output.delete()) throw IOException("Cannot replace temporary HLS output")
            exportCached(context.applicationContext, plan, output, isCancelled)
            checkCancelled(isCancelled)
            validateOutput(plan, output, isCancelled)
            checkCancelled(isCancelled)
            complete = true
        } finally {
            if (!complete) output.delete()
        }
    }

    private fun exportCached(context: Context, plan: HlsDownloadPlan, output: File, isCancelled: () -> Boolean) {
        val thread = HandlerThread("hls-mp4-export").apply { start() }
        val handler = Handler(thread.looper)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Exception>()
        // These resources belong to the export looper, including their final release.
        var cache: SimpleCache? = null
        var transformer: Transformer? = null
        handler.post {
            try {
                val input = HlsDownloadCache.open(context, plan.cacheId).also { cache = it }
                val mediaSources = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(HlsDownloadCache.offlineFactory(input))
                val assets = ExoPlayerAssetLoader.Factory(context, DefaultDecoderFactory(context), Clock.DEFAULT, mediaSources)
                val item = EditedMediaItem.Builder(plan.toMediaItem()).build()

                // Infer the actual tracks: withAudioAndVideoFrom forces synthetic audio for
                // silent videos, which would require an encoder instead of a lossless remux.
                @Suppress("DEPRECATION")
                val sequence = EditedMediaItemSequence.Builder(item).build()
                val composition = Composition.Builder(sequence).setTransmuxAudio(true).setTransmuxVideo(true).build()
                transformer = Transformer.Builder(context)
                    .setLooper(thread.looper)
                    .setAssetLoaderFactory(assets)
                    .setEncoderFactory(NoEncoding)
                    .setMuxerFactory(InAppMp4Muxer.Factory())
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setMaxDelayBetweenMuxerSamplesMs(30_000)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (exportResult.audioEncoderName != null || exportResult.videoEncoderName != null) {
                                failure.compareAndSet(null, IOException("HLS download unexpectedly required encoding"))
                            }
                            finished.countDown()
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            failure.compareAndSet(null, exportException)
                            finished.countDown()
                        }
                    }).build()
                transformer!!.start(composition, output.path)
            } catch (exception: Exception) {
                failure.compareAndSet(null, exception)
                finished.countDown()
            }
        }
        try {
            while (!finished.await(250, TimeUnit.MILLISECONDS)) checkCancelled(isCancelled)
            checkCancelled(isCancelled)
            failure.get()?.let { throw IOException("Could not export cached HLS video", it) }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("HLS export was cancelled").apply { initCause(exception) }
        } finally {
            val released = CountDownLatch(1)
            handler.post {
                try {
                    transformer?.cancel()
                } finally {
                    try {
                        cache?.release()
                    } finally {
                        released.countDown()
                        thread.quitSafely()
                    }
                }
            }
            // Cancellation must not remove a cache/output that the asset loader still owns.
            // Keep short waits and restore interruption only after all readers have released.
            var interrupted = Thread.interrupted()
            while (true) {
                try {
                    if (released.await(250, TimeUnit.MILLISECONDS)) break
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun validateOutput(plan: HlsDownloadPlan, output: File, isCancelled: () -> Boolean) {
        if (output.length() == 0L) throw IOException("HLS export produced an empty file")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(output.path)
            var videoTracks = 0
            var audioTracks = 0
            for (index in 0 until extractor.trackCount) {
                checkCancelled(isCancelled)
                val format = extractor.getTrackFormat(index)
                when (format.getString(MediaFormat.KEY_MIME)) {
                    MimeTypes.VIDEO_H264 -> videoTracks++
                    MimeTypes.AUDIO_AAC -> audioTracks++
                    else -> throw UnsupportedHlsDownloadException("HLS download requires AVC video and AAC audio")
                }
                extractor.selectTrack(index)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var samples = 0
                var lastTimestampUs = 0L
                while (true) {
                    checkCancelled(isCancelled)
                    // Android 6's advance() can succeed after releasing the final sample.
                    // Fetch the next timestamp before flags: its EOS flags are -1, not DRM.
                    val timestampUs = extractor.sampleTime
                    val track = extractor.sampleTrackIndex
                    if (timestampUs == -1L && track == -1) break
                    if (timestampUs < 0 || track != index) throw IOException("HLS export returned invalid sample metadata")
                    val flags = extractor.sampleFlags
                    if (flags < 0) throw IOException("HLS export returned invalid sample flags")
                    if (flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                        throw UnsupportedHlsDownloadException("Encrypted HLS downloads are not supported")
                    }
                    samples++
                    lastTimestampUs = max(lastTimestampUs, timestampUs)
                    if (!extractor.advance()) break
                }
                extractor.unselectTrack(index)
                if (samples == 0) throw IOException("HLS export contains an empty track")
                // Small differences are expected from the last sample and audio padding.
                val toleranceUs = 2_000_000L
                if (lastTimestampUs + toleranceUs < plan.durationUs) throw IOException("HLS export is shorter than the selected playlist")
            }
            if (videoTracks != 1 || audioTracks > 1 || (plan.hasAudio == true && audioTracks != 1)) {
                throw IOException("HLS export did not preserve the selected video and audio tracks")
            }
        } finally {
            extractor.release()
        }
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || isCancelled()) throw InterruptedIOException("HLS export was cancelled")
    }

    @SuppressLint("NewApi")
    private object NoEncoding : Codec.EncoderFactory {
        override fun createForAudioEncoding(format: Format, logSessionId: LogSessionId?): Codec = unsupportedEncoding(format, false)

        override fun createForVideoEncoding(format: Format, logSessionId: LogSessionId?): Codec = unsupportedEncoding(format, true)

        private fun unsupportedEncoding(format: Format, video: Boolean): Nothing = throw ExportException.createForCodec(
            UnsupportedHlsDownloadException("HLS downloads cannot transcode this track"),
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.CodecInfo(format.toString(), video, false, null)
        )
    }
}
