/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import org.schabi.newpipe.extractor.MediaFormat
import us.shandian.giga.postprocessing.Mp3OutputOptions

internal data class DownloadOutputPlan(
    val filename: String,
    val mimeType: String?,
    val estimatedSize: Long
)

/** Resolves the output file metadata for the stream selected in the download dialog. */
internal object DownloadOutputPlanFactory {
    @JvmStatic
    fun forAudio(
        baseFilename: String,
        sourceFormat: MediaFormat?,
        sourceSize: Long,
        durationSeconds: Long,
        mp3OutputSelected: Boolean,
        mp3BitrateKbps: Int
    ): DownloadOutputPlan {
        if (mp3OutputSelected) {
            val estimatedSize = if (Mp3DownloadPolicy.shouldTranscode(true, sourceFormat)) {
                Mp3OutputOptions.estimateRequiredBytes(
                    sourceSize,
                    durationSeconds,
                    mp3BitrateKbps
                )
            } else {
                sourceSize
            }
            return DownloadOutputPlan(
                filename = appendSuffix(baseFilename, MediaFormat.MP3.getSuffix()),
                mimeType = MediaFormat.MP3.mimeType,
                estimatedSize = estimatedSize
            )
        }

        if (sourceFormat == MediaFormat.WEBMA_OPUS) {
            return DownloadOutputPlan(
                filename = appendSuffix(baseFilename, "opus"),
                mimeType = "audio/ogg",
                estimatedSize = sourceSize
            )
        }

        return DownloadOutputPlan(
            filename = appendSuffix(baseFilename, sourceFormat?.getSuffix()),
            mimeType = sourceFormat?.mimeType,
            estimatedSize = sourceSize
        )
    }

    @JvmStatic
    fun forVideo(
        baseFilename: String,
        format: MediaFormat?,
        sourceSize: Long
    ): DownloadOutputPlan {
        return DownloadOutputPlan(
            filename = appendSuffix(baseFilename, format?.getSuffix()),
            mimeType = format?.mimeType,
            estimatedSize = sourceSize
        )
    }

    @JvmStatic
    fun forSubtitle(
        baseFilename: String,
        format: MediaFormat?,
        sourceSize: Long
    ): DownloadOutputPlan {
        val outputSuffix = if (format == MediaFormat.TTML) {
            MediaFormat.SRT.getSuffix()
        } else {
            format?.getSuffix()
        }
        return DownloadOutputPlan(
            filename = appendSuffix(baseFilename, outputSuffix),
            mimeType = format?.mimeType,
            estimatedSize = sourceSize
        )
    }

    private fun appendSuffix(baseFilename: String, suffix: String?): String {
        return "$baseFilename.${suffix.orEmpty()}"
    }
}
