/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package us.shandian.giga.get

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.Downloader
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException
import org.schabi.newpipe.App
import org.schabi.newpipe.download.hls.HlsDownloadCache
import org.schabi.newpipe.download.hls.HlsDownloadDataSource
import org.schabi.newpipe.download.hls.HlsDownloadPlan
import org.schabi.newpipe.download.hls.HlsDownloadPreparation
import org.schabi.newpipe.download.hls.HlsHttpException
import org.schabi.newpipe.download.hls.UnsupportedHlsDownloadException
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManagerService

/** A segmented transport which retains the queue and lifecycle of ordinary download missions. */
open class HlsDownloadMission(urls: Array<String>, storage: StoredFileHelper, kind: Char, postprocessing: Postprocessing) :
    DownloadMission(urls, storage, kind, postprocessing) {
    var hlsPlan: HlsDownloadPlan? = null
        private set

    private var cacheId = UUID.randomUUID().toString()
    private var cachedComplete = false

    @Transient private var generation = 0L

    @Transient private var activeRun: Run? = null

    @Transient private var lastProgressWrite = 0L

    init {
        require(urls.size == 1 && kind == 'v') { "HLS missions require one video playlist." }
    }

    @Synchronized
    override fun start() {
        if (deleted || running || isFinished || urls.isEmpty()) return
        if (cachedComplete) {
            current = urls.size
            super.start()
            return
        }
        if (hasInvalidStorage()) {
            notifyError(ERROR_FILE_CREATION, null)
            return
        }
        val previous = activeRun
        previous?.cancel()
        val run = Run(++generation)
        activeRun = run
        running = true
        errCode = ERROR_NOTHING
        errObject = null
        current = 0
        done = 0
        length = 0
        unknownLength = true
        init = null
        run.thread = Thread({ runWorker(run, previous?.thread) }, "WizeStream-HLS-download")
        threads = arrayOf(run.thread)
        mHandler?.obtainMessage(DownloadManagerService.MESSAGE_RUNNING, this)?.sendToTarget()
        run.thread.start()
    }

    @Synchronized
    override fun pause() {
        if (isPsRunning) {
            super.pause()
            return
        }
        generation++
        activeRun?.cancel()
        super.pause()
    }

    @Synchronized
    override fun cancel(alsoDeleteFile: Boolean): Boolean {
        generation++
        val workers = (threads.orEmpty().toList() + listOfNotNull(activeRun?.thread)).distinct()
        activeRun?.cancel()
        val result = super.cancel(alsoDeleteFile)
        Thread({
            workers.forEach { if (it !== Thread.currentThread()) it.join() }
            removeCache()
        }, "WizeStream-HLS-cleanup").start()
        return result
    }

    @Synchronized
    override fun resetState(rollback: Boolean, persistChanges: Boolean, errorCode: Int) {
        generation++
        activeRun?.cancel()
        super.resetState(rollback, false, errorCode)
        if (rollback && cachedComplete) {
            // A failed export can be retried from the same complete offline input.
            current = urls.size
            psState = 0
        }
        if (persistChanges) writeThisToFile()
    }

    override fun isInitialized(): Boolean = hlsPlan != null

    @Synchronized
    override fun notifyFinished() {
        super.notifyFinished()
        if (isFinished && !running) removeCache()
    }

    protected open fun context(): Context = App.instance

    /** Overridable transport boundary lets lifecycle tests run without provider traffic. */
    protected open fun download(
        cacheId: String,
        isCurrent: () -> Boolean,
        progress: (contentLength: Long, bytesDownloaded: Long) -> Unit
    ): HlsDownloadPlan {
        val headers = DownloadRequestHeaders.at(this, 0)
        val transport = HlsDownloadDataSource.Factory(urls[0], headers)
        val cache = HlsDownloadCache.open(context(), cacheId)
        synchronized(this) {
            if (!isCurrent()) {
                cache.release()
                throw InterruptedIOException("The HLS download was cancelled.")
            }
            activeRun?.transport = transport
        }
        try {
            val prepared = hlsPlan ?: HlsDownloadPreparation.prepare(
                cache,
                cacheId,
                urls[0],
                headers,
                HlsDownloadPreparation.desiredHeight(recoveryInfo?.firstOrNull()?.desired),
                transport
            ).also {
                synchronized(this) {
                    if (!isCurrent()) throw InterruptedIOException("The HLS download was cancelled.")
                    hlsPlan = it
                    writeThisToFile()
                }
            }
            var attempts = 0
            while (true) {
                if (!isCurrent()) throw InterruptedIOException("The HLS download was cancelled.")
                val downloader = HlsDownloader.Factory(HlsDownloadCache.networkFactory(cache, transport))
                    .setExecutor { it.run() }
                    .create(prepared.toMediaItem())
                synchronized(this) {
                    if (!isCurrent()) throw InterruptedIOException("The HLS download was cancelled.")
                    activeRun?.downloader = downloader
                }
                try {
                    downloader.download { contentLength, bytesDownloaded, _ -> progress(contentLength, bytesDownloaded) }
                    return prepared
                } catch (error: IOException) {
                    if (!isCurrent() || error is UnsupportedHlsDownloadException || error is HlsHttpException && error.responseCode < 500 || attempts++ >= maxRetry.coerceIn(0, 5)) {
                        throw error
                    }
                } finally {
                    downloader.cancel()
                }
            }
        } finally {
            transport.cancel()
            cache.release()
        }
    }

    private fun runWorker(run: Run, previous: Thread?) {
        try {
            // A rapid resume waits off the UI thread until the old cache owner has released it.
            awaitPrevious(previous)
            if (!isCurrent(run)) return
            val plan = download(cacheId, { isCurrent(run) }) { total, bytes ->
                if (total > MAX_DOWNLOAD_BYTES || bytes > MAX_DOWNLOAD_BYTES) throw UnsupportedHlsDownloadException("The HLS video exceeds the supported download size.")
                synchronized(this) {
                    if (!isCurrent(run)) throw InterruptedIOException("The HLS download was cancelled.")
                    done = bytes
                    length = if (total >= 0) total else bytes
                    unknownLength = total == C.LENGTH_UNSET.toLong()
                    val now = System.nanoTime()
                    if (now - lastProgressWrite >= 1_000_000_000L) {
                        lastProgressWrite = now
                        writeThisToFile()
                    }
                }
            }
            synchronized(this) {
                if (!isCurrent(run)) return
                hlsPlan = plan
                cachedComplete = true
                current = urls.size
                unknownLength = false
                activeRun = null
                writeThisToFile()
                notifyFinished()
            }
        } catch (error: Exception) {
            synchronized(this) {
                if (!isCurrent(run) || error is InterruptedException || error is InterruptedIOException && Thread.currentThread().isInterrupted) return
                val code = when (error) {
                    is HlsHttpException -> error.responseCode
                    is SocketTimeoutException -> ERROR_TIMEOUT
                    is UnknownHostException -> ERROR_UNKNOWN_HOST
                    is ConnectException -> ERROR_CONNECT_HOST
                    is SSLException -> ERROR_SSL_EXCEPTION
                    else -> ERROR_UNKNOWN_EXCEPTION
                }
                notifyError(code, error)
            }
        } finally {
            synchronized(this) {
                if (activeRun === run) activeRun = null
            }
            if (deleted) removeCache()
        }
    }

    @Synchronized
    private fun isCurrent(run: Run): Boolean = activeRun === run && generation == run.generation && running && !deleted && !Thread.currentThread().isInterrupted

    private fun awaitPrevious(previous: Thread?) {
        if (previous == null || previous === Thread.currentThread()) return
        var interrupted = false
        while (previous.isAlive) {
            try {
                previous.join()
            } catch (_: InterruptedException) {
                // Keep the predecessor chain alive until its cache is released. A later resume
                // may already be waiting for this worker, even though this run was cancelled.
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun removeCache() {
        try {
            HlsDownloadCache.remove(context(), cacheId)
        } catch (_: Exception) {
            // A completed output remains valid if optional temporary-input cleanup fails.
        }
    }

    private class Run(val generation: Long) {
        lateinit var thread: Thread
        var downloader: Downloader? = null
        var transport: HlsDownloadDataSource.Factory? = null

        fun cancel() {
            downloader?.cancel()
            transport?.cancel()
            thread.interrupt()
        }
    }

    companion object {
        private const val serialVersionUID = 1L
        private const val MAX_DOWNLOAD_BYTES = 32L * 1024 * 1024 * 1024
    }
}
