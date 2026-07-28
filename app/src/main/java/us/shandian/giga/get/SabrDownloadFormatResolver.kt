package us.shandian.giga.get

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import java.io.File
import java.io.IOException

internal object SabrDownloadFormatResolver {
    @Throws(IOException::class)
    fun resolveInfo(recoveries: Array<MissionRecoveryInfo>): YoutubeSabrInfo {
        return recoveries.firstNotNullOfOrNull {
            it.deliveryMethodInfo as? YoutubeSabrInfo
        } ?: throw IOException("Missing SABR info")
    }

    @Throws(IOException::class)
    fun selectedAudioFormat(
        info: YoutubeSabrInfo,
        recoveries: Array<MissionRecoveryInfo>,
    ): YoutubeSabrFormat {
        val audioRecovery = recoveries.firstOrNull { it.kind == 'a' }
        return audioRecovery?.let { findAudioFormat(info, it) }
            ?: if (recoveries.any { it.kind == 'v' }) {
                findLightweightAudioFormat(info)
            } else {
                null
            }
            ?: info.findBestAudioFormat()
            ?: throw SabrDownloadException(
                SabrDownloadException.Reason.FORMAT,
                "SABR download failed: missing audio format",
            )
    }

    @Throws(IOException::class)
    fun selectedVideoFormat(
        info: YoutubeSabrInfo,
        recoveries: Array<MissionRecoveryInfo>,
    ): YoutubeSabrFormat {
        val videoRecovery = recoveries.firstOrNull { it.kind == 'v' }
        return videoRecovery?.let { findVideoFormat(info, it) }
            ?: if (recoveries.any { it.kind == 'a' }) {
                findLightweightVideoFormat(info)
            } else {
                null
            }
            ?: info.findLowestVideoFormat()
            ?: throw SabrDownloadException(
                SabrDownloadException.Reason.FORMAT,
                "SABR download failed: missing video format",
            )
    }

    @Throws(IOException::class)
    fun buildTargets(
        info: YoutubeSabrInfo,
        recoveries: Array<MissionRecoveryInfo>,
        workDir: File,
    ): List<SabrDownloadTarget> {
        return recoveries.mapIndexed { index, recovery ->
            val format = when (recovery.kind) {
                'a' -> findAudioFormat(info, recovery)
                'v' -> findVideoFormat(info, recovery)
                else -> throw SabrDownloadException(
                    SabrDownloadException.Reason.FORMAT,
                    "SABR download failed: unsupported resource kind ${recovery.kind}",
                )
            }
            SabrDownloadTarget(index, recovery, format, File(workDir, "input-$index.media"))
        }
    }

    @Throws(IOException::class)
    private fun findAudioFormat(
        info: YoutubeSabrInfo,
        recovery: MissionRecoveryInfo,
    ): YoutubeSabrFormat {
        return info.formats.firstOrNull { format ->
            format.isAudio &&
                (recovery.itag <= 0 || format.itag == recovery.itag) &&
                (recovery.audioTrackId == null || recovery.audioTrackId == format.audioTrackId)
        } ?: throw SabrDownloadException(
            SabrDownloadException.Reason.FORMAT,
            "SABR download failed: could not resolve audio itag ${recovery.itag}",
        )
    }

    @Throws(IOException::class)
    private fun findVideoFormat(
        info: YoutubeSabrInfo,
        recovery: MissionRecoveryInfo,
    ): YoutubeSabrFormat {
        return info.formats.firstOrNull { format ->
            format.isVideo && (recovery.itag <= 0 || format.itag == recovery.itag)
        } ?: throw SabrDownloadException(
            SabrDownloadException.Reason.FORMAT,
            "SABR download failed: could not resolve video itag ${recovery.itag}",
        )
    }

    private fun findLightweightAudioFormat(info: YoutubeSabrInfo): YoutubeSabrFormat? {
        return info.formats
            .filter { it.isAudio }
            .sortedWith(
                compareBy<YoutubeSabrFormat> { !it.isOriginalAudio }
                    .thenBy { it.isDrc }
                    .thenBy { normalizedBitrate(it) },
            )
            .firstOrNull()
    }

    private fun findLightweightVideoFormat(info: YoutubeSabrInfo): YoutubeSabrFormat? {
        return info.formats
            .filter { it.isVideo }
            .sortedWith(
                compareBy<YoutubeSabrFormat> { normalizedHeight(it) }
                    .thenBy { normalizedBitrate(it) },
            )
            .firstOrNull()
    }

    private fun normalizedBitrate(format: YoutubeSabrFormat): Int {
        return format.bitrate.takeIf { it > 0 } ?: Int.MAX_VALUE
    }

    private fun normalizedHeight(format: YoutubeSabrFormat): Int {
        return format.height.takeIf { it > 0 } ?: Int.MAX_VALUE
    }
}
