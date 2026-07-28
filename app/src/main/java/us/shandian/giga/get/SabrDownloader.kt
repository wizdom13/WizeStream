package us.shandian.giga.get

import android.util.Log
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrProtocolException
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.player.datasource.LocalDomPoTokenProvider
import org.schabi.newpipe.player.datasource.SabrPolicyRuntime
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class SabrDownloader(
    private val mission: DownloadMission,
) : Runnable {
    override fun run() {
        try {
            ensureRunning()
            val recoveries = validateRecoveryInfo()
            var info = SabrDownloadFormatResolver.resolveInfo(recoveries)

            val expectedLength = recoveries.map { recovery ->
                when (recovery.kind) {
                    'a' -> SabrDownloadFormatResolver.selectedAudioFormat(info, arrayOf(recovery))
                    'v' -> SabrDownloadFormatResolver.selectedVideoFormat(info, arrayOf(recovery))
                    else -> null
                }
            }.takeIf { formats -> formats.all { it != null && it.contentLength > 0 } }
                ?.sumOf { it!!.contentLength }
                ?: 0L
            prepareMission(expectedLength)
            var coldStartAttempts = 0
            var transientAttempts = 0
            var refreshInfo = false
            while (true) {
                try {
                    if (refreshInfo) {
                        info = SabrDownloadFormatResolver.resolveInfo(recoveries)
                        refreshInfo = false
                    }
                    runSessionAttempt(info, recoveries, coldStartAttempts)
                    break
                } catch (error: RetryColdStartException) {
                    coldStartAttempts++
                    if (coldStartAttempts > MAX_COLD_START_RETRIES) {
                        throw SabrDownloadException(
                            SabrDownloadException.Reason.INITIALIZATION,
                            "SABR download failed: cold start did not provide initialization",
                            error,
                        )
                    }
                    logDebug("retry cold start attempt=$coldStartAttempts")
                    refreshInfo = true
                } catch (error: Exception) {
                    if (!isRetryableAttemptFailure(error)) {
                        throw error
                    }
                    if (transientAttempts >= MAX_TRANSIENT_RETRIES) {
                        throw SabrDownloadException(
                            SabrDownloadException.Reason.NETWORK,
                            "SABR download failed: network error after retries",
                            error,
                        )
                    }
                    transientAttempts++
                    logDebug("retry transient attempt=$transientAttempts error=${error.javaClass.simpleName}")
                    Thread.sleep(transientRetryDelayMs(transientAttempts))
                    refreshInfo = true
                }
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: SabrProtocolException) {
            notifyErrorAndCleanup(classifyProtocolException(error))
        } catch (error: Exception) {
            notifyErrorAndCleanup(error)
        }
    }

    @Throws(IOException::class, InterruptedException::class, SabrProtocolException::class)
    private fun runSessionAttempt(
        info: YoutubeSabrInfo,
        recoveries: Array<MissionRecoveryInfo>,
        coldStartAttempt: Int,
    ) {
        val session = YoutubeSabrSession(
            info,
            SabrDownloadFormatResolver.selectedAudioFormat(info, recoveries),
            SabrDownloadFormatResolver.selectedVideoFormat(info, recoveries),
            LocalDomPoTokenProvider(mission.context),
            null,
            SabrPolicyRuntime.createSessionHost(),
        )
        val workDir = prepareWorkDirectory()
        val targets = SabrDownloadFormatResolver.buildTargets(info, recoveries, workDir)
        restoreTargets(targets)
        targets.forEach { target ->
            session.streamState.jumpBufferedTo(target.format, target.nextWriteSequence)
        }
        configureRequestMode(session, targets, coldStartAttempt)
        val outputs = mutableMapOf<Int, FileOutputStream>()
        try {
            targets.forEach { target ->
                outputs[target.resourceIndex] = FileOutputStream(target.file, true)
            }
        } catch (error: IOException) {
            outputs.values.forEach { output ->
                try {
                    output.close()
                } catch (ignored: IOException) {
                }
            }
            throw storageException("could not open temporary media", error)
        }

        try {
            downloadSegments(
                session,
                targets,
                SabrSegmentWriter(session, targets, outputs, ::reportBytesWritten),
            )
        } finally {
            outputs.values.forEach { output ->
                try {
                    output.close()
                } catch (ignored: Exception) {
                    // Nothing to do.
                }
            }
            session.clearCache()
        }

        ensureRunning()
        val finalBytes = SabrFfmpegMuxer(mission).remuxAndCopy(
            targets.map { it.file },
            targets,
            workDir,
        )
        completeMission(finalBytes)
    }

    @Throws(IOException::class)
    private fun validateRecoveryInfo(): Array<MissionRecoveryInfo> {
        val recoveries = mission.recoveryInfo ?: throw IOException("Missing SABR recovery info")
        if (recoveries.size != mission.urls.size || recoveries.any { !it.isSabr }) {
            throw SabrDownloadException(
                SabrDownloadException.Reason.FORMAT,
                "SABR download failed: mixed SABR/non-SABR resources are not supported",
            )
        }
        return recoveries
    }

    private fun prepareMission(expectedLength: Long) {
        if (mission.sabrCheckpoint?.version != SabrDownloadCheckpoint.VERSION) {
            mission.sabrCheckpoint = null
            cleanup(mission)
        }
        mission.done = restoredProgress()
        mission.nearLength = expectedLength.coerceAtLeast(mission.nearLength)
        mission.unknownLength = mission.nearLength <= 0
        mission.sabrStarted = true
        if (mission.nearLength > 0) {
            mission.length = mission.length
                .coerceAtLeast(mission.nearLength)
                .coerceAtLeast(mission.done)
        }
        mission.current = 0
        mission.writeThisToFile()
    }

    private fun reportBytesWritten(target: SabrDownloadTarget, delta: Long) {
        if (delta <= 0) {
            return
        }
        updateCheckpoint(target)
        if (mission.nearLength > 0) {
            mission.length = mission.length
                .coerceAtLeast(mission.nearLength)
                .coerceAtLeast(mission.done + delta)
            mission.unknownLength = false
        }
        mission.notifyProgress(delta)
    }

    private fun configureRequestMode(
        session: YoutubeSabrSession,
        targets: List<SabrDownloadTarget>,
        coldStartAttempt: Int,
    ) {
        val useCompanionWarmup = targets.size == 1 && coldStartAttempt % 2 == 1
        if (useCompanionWarmup) {
            session.streamState.setVideoAndAudioRequestMode()
        } else if (targets.size == 1 && targets.first().format.isAudio) {
            session.streamState.setAudioOnlyRequestMode()
        } else if (targets.size == 1 && targets.first().format.isVideo) {
            session.streamState.setVideoOnlyRequestMode()
        } else {
            session.streamState.setVideoAndAudioRequestMode()
        }
    }

    @Throws(IOException::class)
    private fun prepareWorkDirectory(): File {
        val workDir = workDirectory(mission)
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw storageException("could not create temporary directory", null)
        }
        return workDir
    }

    private fun restoreTargets(targets: List<SabrDownloadTarget>) {
        try {
            targets.forEach { target ->
                val checkpoint = mission.sabrCheckpoint?.resources?.firstOrNull {
                    it.resourceIndex == target.resourceIndex &&
                        it.itag == target.format.itag &&
                        it.tempFilePath == target.file.absolutePath &&
                        it.nextWriteSequence > 0 &&
                        it.bytesWritten >= it.initializationBytes &&
                        it.initializationBytes >= 0 &&
                        it.initializationBytes <= MAX_INITIALIZATION_BYTES &&
                        target.file.exists() &&
                        target.file.length() >= it.bytesWritten
                }
                if (checkpoint == null) {
                    if (target.file.exists() && !target.file.delete()) {
                        throw IOException("Could not reset ${target.file}")
                    }
                    return@forEach
                }
                RandomAccessFile(target.file, "rw").use { file ->
                    file.setLength(checkpoint.bytesWritten)
                    if (checkpoint.initializationBytes > 0) {
                        val initialization = ByteArray(checkpoint.initializationBytes)
                        file.seek(0)
                        file.readFully(initialization)
                        target.initializationData = initialization
                        target.initializationWritten = true
                    }
                }
                target.nextWriteSequence = checkpoint.nextWriteSequence
            }
        } catch (error: IOException) {
            throw storageException("could not restore temporary media", error)
        }
    }

    private fun storageException(message: String, cause: IOException?): SabrDownloadException {
        return SabrDownloadException(
            SabrDownloadException.Reason.STORAGE,
            "SABR download failed: $message",
            cause,
        )
    }

    private fun updateCheckpoint(target: SabrDownloadTarget) {
        val current = mission.sabrCheckpoint ?: SabrDownloadCheckpoint()
        val resources = current.resources
            .filterNot { it.resourceIndex == target.resourceIndex }
            .toMutableList()
        val previousInitializationBytes = current.resources
            .firstOrNull { it.resourceIndex == target.resourceIndex }
            ?.initializationBytes
            ?: 0
        resources += SabrResourceCheckpoint(
            resourceIndex = target.resourceIndex,
            itag = target.format.itag,
            tempFilePath = target.file.absolutePath,
            nextWriteSequence = target.nextWriteSequence,
            bytesWritten = target.file.length(),
            initializationBytes = if (target.initializationWritten && previousInitializationBytes == 0) {
                target.initializationData?.size ?: 0
            } else {
                previousInitializationBytes
            },
        )
        mission.sabrCheckpoint = current.copy(resources = resources.sortedBy { it.resourceIndex })
    }

    private fun restoredProgress(): Long {
        return mission.sabrCheckpoint?.resources
            ?.filter { checkpoint ->
                File(checkpoint.tempFilePath).let { it.exists() && it.length() >= checkpoint.bytesWritten }
            }
            ?.sumOf { it.bytesWritten }
            ?: 0L
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadSegments(
        session: YoutubeSabrSession,
        targets: List<SabrDownloadTarget>,
        writer: SabrSegmentWriter,
    ) {
        val localization = Localization("en", "US")
        writer.observeWrittenInitializations()
        if (targets.size == 1 && !targets.first().initializationWritten) {
            fetchInitializationsOrRetry(writer, localization)
            writer.observeWrittenInitializations()
            writer.drainCachedInitializations()
        }

        var emptyResponses = 0
        while (true) {
            ensureRunning()
            writer.observeWrittenInitializations()
            var wroteSegment = writer.drainCachedInitializations()
            wroteSegment = writer.drainCachedSegments() || wroteSegment
            configureInitializedSingleTargetMode(session, targets)

            if (isDownloadComplete(session, targets)) {
                break
            }

            val playerTimeMs = downloadPlayerTimeMs(session, targets)
            session.streamState.setPlayerTimeMs(playerTimeMs)
            val segmentCount = session.pumpOnceStreaming(localization)
            writer.observeWrittenInitializations()
            wroteSegment = writer.drainCachedInitializations() || wroteSegment
            wroteSegment = writer.drainCachedSegments() || wroteSegment
            enforceSessionCacheLimit(session, writer)
            configureInitializedSingleTargetMode(session, targets)
            if (hasMediaWaitingForInitialization(targets)) {
                fetchMissingInitializationsOrRetry(writer, localization)
                writer.observeWrittenInitializations()
                wroteSegment = writer.drainCachedInitializations() || wroteSegment
                wroteSegment = writer.drainCachedSegments() || wroteSegment
                configureInitializedSingleTargetMode(session, targets)
                if (hasMediaWaitingForInitialization(targets)) {
                    throw RetryColdStartException()
                }
            }

            if (isDownloadComplete(session, targets)) {
                break
            }
            if (wroteSegment || segmentCount > 0) {
                emptyResponses = 0
            } else {
                emptyResponses++
                if (emptyResponses > MAX_EMPTY_RESPONSES) {
                    throw SabrDownloadException(
                        SabrDownloadException.Reason.STALLED,
                        "SABR download stalled: no media received after $MAX_EMPTY_RESPONSES rounds",
                    )
                }
                Thread.sleep(IDLE_POLL_MS)
            }
        }
    }

    @Throws(IOException::class)
    private fun fetchInitializationsOrRetry(
        writer: SabrSegmentWriter,
        localization: Localization,
    ) {
        try {
            writer.fetchUnwrittenInitializations(localization)
        } catch (error: SabrProtocolException) {
            if (isRetryableInitializationProtocolError(error)) {
                throw RetryColdStartException(error)
            }
            throw error
        }
    }

    @Throws(IOException::class)
    private fun fetchMissingInitializationsOrRetry(
        writer: SabrSegmentWriter,
        localization: Localization,
    ) {
        try {
            writer.fetchMissingInitializations(localization)
        } catch (error: SabrProtocolException) {
            if (isRetryableInitializationProtocolError(error)) {
                throw RetryColdStartException(error)
            }
            throw error
        }
    }

    @Throws(IOException::class)
    private fun enforceSessionCacheLimit(
        session: YoutubeSabrSession,
        writer: SabrSegmentWriter,
    ) {
        if (session.cachedBytes <= MAX_SESSION_CACHE_BYTES) {
            return
        }
        writer.drainCachedSegments()
        if (session.cachedBytes <= MAX_SESSION_CACHE_BYTES) {
            return
        }
        throw SabrDownloadException(
            SabrDownloadException.Reason.STALLED,
            "SABR download stalled: cached media grew to ${session.cachedBytes} bytes",
        )
    }

    private fun configureInitializedSingleTargetMode(
        session: YoutubeSabrSession,
        targets: List<SabrDownloadTarget>,
    ) {
        if (targets.size != 1 || !targets.first().initializationWritten) {
            return
        }
        if (targets.first().format.isAudio) {
            session.streamState.setAudioOnlyRequestMode()
        } else {
            session.streamState.setVideoOnlyRequestMode()
        }
    }

    private fun hasMediaWaitingForInitialization(targets: List<SabrDownloadTarget>): Boolean {
        return targets.any { target -> !target.initializationWritten && target.pending.isNotEmpty() }
    }

    private fun downloadPlayerTimeMs(
        session: YoutubeSabrSession,
        targets: List<SabrDownloadTarget>,
    ): Long {
        if (targets.size == 1) {
            return session.streamState.getBufferedEndMs(targets.first().format)
        }
        return session.streamState.minBufferedEndMs
    }

    private fun isDownloadComplete(
        session: YoutubeSabrSession,
        targets: List<SabrDownloadTarget>,
    ): Boolean {
        return targets.all { target ->
            target.pending.isEmpty() &&
                (session.streamState.isComplete(target.format) ||
                    session.isBeyondEnd(SabrSegmentRequest.media(target.format, target.nextWriteSequence)))
        }
    }

    private fun completeMission(finalBytes: Long) {
        if (finalBytes > 0) {
            mission.done = finalBytes
            mission.length = finalBytes
        }
        mission.current = mission.urls.size
        mission.psState = 2
        mission.sabrCheckpoint = null
        cleanup(mission)
        mission.unknownLength = false
        mission.notifyFinished()
    }

    @Throws(InterruptedException::class)
    private fun ensureRunning() {
        if (!mission.running || Thread.currentThread().isInterrupted) {
            throw InterruptedException()
        }
    }

    private fun notifyErrorAndCleanup(error: Exception) {
        if (mission.running) {
            mission.notifyError(error)
        }
    }

    private fun isRetryableAttemptFailure(error: Exception): Boolean {
        if (error is RetryColdStartException || error is SabrDownloadException) {
            return false
        }
        if (error is SabrRecoverableException) {
            return true
        }
        if (error is SabrProtocolException) {
            return false
        }
        return error is SocketTimeoutException ||
            error is ConnectException ||
            error is UnknownHostException ||
            error is IOException
    }

    private fun transientRetryDelayMs(attempt: Int): Long {
        return (500L shl (attempt - 1)).coerceAtMost(MAX_TRANSIENT_RETRY_DELAY_MS)
    }

    private fun classifyProtocolException(error: SabrProtocolException): SabrDownloadException {
        val message = error.message.orEmpty()
        val reason = when {
            message.contains("protected", ignoreCase = true) ||
                message.contains("PO token", ignoreCase = true) -> {
                SabrDownloadException.Reason.PROTECTED
            }
            message.contains("policy-only", ignoreCase = true) ||
                message.contains("not returned", ignoreCase = true) ||
                message.contains("integrity", ignoreCase = true) -> {
                SabrDownloadException.Reason.STALLED
            }
            else -> SabrDownloadException.Reason.PROTOCOL
        }
        return SabrDownloadException(
            reason,
            "SABR download failed: ${message.ifBlank { "protocol error" }}",
            error,
        )
    }

    private fun isRetryableInitializationProtocolError(error: SabrProtocolException): Boolean {
        val message = error.message.orEmpty()
        if (!message.contains(":init")) {
            return false
        }
        return message.contains("policy-only", ignoreCase = true) ||
            message.contains("not returned", ignoreCase = true)
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "SabrDownloader"
        private const val IDLE_POLL_MS = 250L
        private const val MAX_EMPTY_RESPONSES = 60
        private const val MAX_COLD_START_RETRIES = 3
        private const val MAX_TRANSIENT_RETRIES = 5
        private const val MAX_TRANSIENT_RETRY_DELAY_MS = 5_000L
        private const val MAX_SESSION_CACHE_BYTES = 48L * 1024L * 1024L
        private const val MAX_INITIALIZATION_BYTES = 16 * 1024 * 1024

        @JvmStatic
        fun cleanup(mission: DownloadMission) {
            try {
                workDirectory(mission).deleteRecursively()
            } catch (ignored: Exception) {
                // Nothing to do.
            }
        }

        private fun workDirectory(mission: DownloadMission): File {
            val base = mission.context.getExternalFilesDir(null) ?: mission.context.filesDir
            val missionId = if (mission.timestamp > 0) {
                mission.timestamp.toString()
            } else {
                mission.storage.name.hashCode().toString()
            }
            return File(base, "sabr-downloader/$missionId")
        }
    }

    private class RetryColdStartException(cause: Throwable? = null) : IOException(cause)
}
