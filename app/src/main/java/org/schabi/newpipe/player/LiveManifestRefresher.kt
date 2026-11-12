/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifest
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.YoutubeDashLiveManifestParser
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.mediasource.YoutubeLiveMediaSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.ExtractorHelper

internal data class LiveManifestCandidate(val info: StreamInfo, val manifest: DashManifest)

/** Rotate compatible DASH URLs without releasing prepared periods or queued samples. */
internal class LiveManifestRefresher(
    private val player: Player,
    private val requireDvr: (String) -> Boolean,
    private val load: (PlayQueueItem) -> Single<LiveManifestCandidate> = ::loadCandidate,
    private val worker: Scheduler = Schedulers.io()
) {
    private var request: Disposable? = null
    private var generation = 0

    fun refresh(engine: ExoPlayer, item: PlayQueueItem, complete: (Boolean) -> Unit): Boolean {
        if (InvidiousBackend.isEnabled() || item.isDirectMedia) return false
        val dash = source(engine) ?: return false
        val window = window(engine) ?: return false
        val previous = window.manifest as? DashManifest ?: return false
        val formats = listOfNotNull(engine.videoFormat, engine.audioFormat)
        if (formats.isEmpty()) return false
        if (request?.isDisposed == false) return true
        val token = generation
        val started = SystemClock.elapsedRealtime()
        request = Single.defer { load(item) }
            .subscribeOn(worker)
            .timeout(15, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { candidate ->
                    request = null
                    if (!active(engine, item, token) || source(engine) !== dash) return@subscribe
                    val current = window(engine) ?: return@subscribe complete(false)
                    val positionUs = current.positionInFirstPeriodUs + engine.currentPosition * 1_000
                    val info = candidate.info
                    val currentFormats = listOfNotNull(engine.videoFormat, engine.audioFormat)
                    val accepted = info.serviceId == item.serviceId && info.url == item.url &&
                        info.streamType == StreamType.LIVE_STREAM && safeManifestUrl(info.dashMpdUrl) &&
                        compatible(previous, candidate.manifest, currentFormats, positionUs, requireDvr(item.url), SystemClock.elapsedRealtime() - started)
                    if (accepted) {
                        // The next normal manifest load uses the new URI. Media3 retains its
                        // existing MediaPeriods, sample queues, decoder and playback position.
                        dash.replaceManifestUri(Uri.parse(info.dashMpdUrl))
                        Log.i(Player.TAG, "Refreshed compatible live DASH URI without rebuilding playback")
                    }
                    complete(accepted)
                },
                { error ->
                    request = null
                    if (active(engine, item, token)) {
                        Log.i(Player.TAG, "Live DASH refresh unavailable: ${error.javaClass.simpleName}")
                        complete(false)
                    }
                }
            )
        return true
    }

    private fun active(engine: ExoPlayer, item: PlayQueueItem, token: Int): Boolean = generation == token &&
        player.getExoPlayer() === engine && player.playQueue?.item === item && player.playWhenReady &&
        !InvidiousBackend.isEnabled() &&
        (engine.playbackState == Media3Player.STATE_READY || engine.playbackState == Media3Player.STATE_BUFFERING)

    fun cancel() {
        generation++
        request?.dispose()
        request = null
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 4L * 1024 * 1024

        private fun window(engine: ExoPlayer): Timeline.Window? = engine.currentTimeline.takeUnless { it.isEmpty }
            ?.getWindow(engine.currentMediaItemIndex, Timeline.Window())

        private fun source(engine: ExoPlayer): DashMediaSource? {
            val loaded = MediaItemTag.from(engine.currentMediaItem)
                .flatMap { it.getMaybeExtras(LoadedMediaSource::class.java) }.orElse(null) ?: return null
            return when (val source = loaded.sourceForLiveRefresh) {
                is DashMediaSource -> source
                is YoutubeLiveMediaSource -> source.dashForRefresh()
                else -> null
            }
        }

        private fun loadCandidate(item: PlayQueueItem): Single<LiveManifestCandidate> = ExtractorHelper.getStreamInfo(item.serviceId, item.url, true)
            .flatMap { info ->
                Single.create { emitter ->
                    if (InvidiousBackend.isEnabled() || !safeManifestUrl(info.dashMpdUrl)) {
                        emitter.tryOnError(IOException("Direct live refresh unavailable"))
                        return@create
                    }
                    val client = DownloaderImpl.getInstance().client.newBuilder()
                        .followRedirects(false).followSslRedirects(false).build()
                    val call = client.newCall(Request.Builder().url(info.dashMpdUrl).build())
                    call.timeout().timeout(10, TimeUnit.SECONDS)
                    emitter.setCancellable(call::cancel)
                    try {
                        call.execute().use { response ->
                            if (!response.isSuccessful) throw IOException("Live manifest rejected")
                            val body = response.body ?: throw IOException("Empty live manifest")
                            if (body.contentLength() > MAX_MANIFEST_BYTES || body.source().request(MAX_MANIFEST_BYTES + 1)) {
                                throw IOException("Live manifest too large")
                            }
                            val manifest = YoutubeDashLiveManifestParser().parse(Uri.parse(info.dashMpdUrl), ByteArrayInputStream(body.bytes()))
                            if (!emitter.isDisposed) emitter.onSuccess(LiveManifestCandidate(info, manifest))
                        }
                    } catch (error: Exception) {
                        emitter.tryOnError(error)
                    }
                }
            }

        internal fun safeManifestUrl(url: String): Boolean = try {
            val uri = URI(url)
            uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null && InvidiousBackend.isYoutubeHost(uri.host)
        } catch (_: Exception) {
            false
        }

        internal fun compatible(old: DashManifest, fresh: DashManifest, formats: List<Format>, positionUs: Long, protectedDvr: Boolean, elapsedMs: Long): Boolean {
            if (!old.dynamic || !fresh.dynamic || fresh.minUpdatePeriodMs == C.TIME_UNSET ||
                fresh.durationMs != C.TIME_UNSET || old.periodCount != 1 || fresh.periodCount != 1 ||
                old.availabilityStartTimeMs != fresh.availabilityStartTimeMs || positionUs < 0 || formats.isEmpty()
            ) {
                return false
            }
            if (protectedDvr && fresh.timeShiftBufferDepthMs != C.TIME_UNSET &&
                (old.timeShiftBufferDepthMs == C.TIME_UNSET || fresh.timeShiftBufferDepthMs < old.timeShiftBufferDepthMs)
            ) {
                return false
            }
            val a = old.getPeriod(0)
            val b = fresh.getPeriod(0)
            if (a.id != b.id || a.startMs != b.startMs || a.adaptationSets.size != b.adaptationSets.size) return false
            // Media3 retains representation indices as well as IDs. A reordered ladder or
            // a changed codec needs the normal bounded rebuild instead of an in-place edit.
            a.adaptationSets.zip(b.adaptationSets).forEach { (x, y) ->
                if (x.id != y.id || x.type != y.type || x.representations.size != y.representations.size) return false
                x.representations.zip(y.representations).forEach { (p, q) ->
                    if (p.format.id != q.format.id || p.format.sampleMimeType != q.format.sampleMimeType ||
                        p.format.codecs != q.format.codecs || p.presentationTimeOffsetUs != q.presentationTimeOffsetUs
                    ) {
                        return false
                    }
                }
            }
            val before = a.adaptationSets.flatMap { it.representations }
            val after = b.adaptationSets.flatMap { it.representations }
            return formats.all { format ->
                val index = before.indexOfFirst { it.format.id == format.id && it.format.sampleMimeType == format.sampleMimeType }
                if (index < 0) return@all false
                val previous = before[index].index ?: return@all false
                val next = after[index].index ?: return@all false
                val count = next.getSegmentCount(fresh.getPeriodDurationUs(0))
                if (count <= 0) return@all false
                val first = next.firstSegmentNum
                val last = first + count - 1
                val startUs = next.getTimeUs(first)
                val endUs = next.getTimeUs(last) + next.getDurationUs(last, fresh.getPeriodDurationUs(0))
                positionUs >= startUs && positionUs < endUs &&
                    (!protectedDvr || startUs <= previous.getTimeUs(previous.firstSegmentNum) + (elapsedMs.coerceAtLeast(0) + 10_000) * 1_000)
            }
        }
    }
}
