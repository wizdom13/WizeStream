/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package us.shandian.giga.postprocessing

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import org.schabi.newpipe.App
import org.schabi.newpipe.download.hls.HlsMp4Exporter
import org.schabi.newpipe.streams.io.SharpStream
import us.shandian.giga.get.HlsDownloadMission

/** Retains cached input after a failed final write so postprocessing can be retried. */
internal class HlsToMp4Postprocessing : Postprocessing(false, false, ALGORITHM_HLS_TO_MP4) {
    override fun process(out: SharpStream?, vararg sources: SharpStream?): Int {
        val target = mission as? HlsDownloadMission ?: throw IOException("HLS export requires an HLS download")
        val plan = target.hlsPlan ?: throw IOException("HLS download has no completed input")
        val output = temporalFile ?: throw IOException("HLS export has no temporary directory")
        try {
            HlsMp4Exporter.export(App.instance, plan, output, ::isCancellationRequested)
            copyOutput(target, output)
            return OK_RESULT.toInt()
        } finally {
            output.delete()
        }
    }

    internal companion object {
        /** Copies only the completed MP4; failed writes leave private HLS input available to retry. */
        fun copyOutput(target: HlsDownloadMission, output: File) {
            try {
                checkCancellation(target)
                output.inputStream().use { source ->
                    target.storage.stream.use { destination ->
                        checkCancellation(target)
                        destination.setLength(0)
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            checkCancellation(target)
                            val count = source.read(buffer)
                            if (count < 0) break
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                checkCancellation(target)
                target.length = target.storage.length()
                target.done = target.length
            } finally {
                // Opening direct storage can recreate a file deleted by cancel(true). Wait until
                // all handles close before removing it again, and honour cancel(false).
                if (target.deleted && target.deleteFileOnCancel) target.storage.delete()
            }
        }

        private fun checkCancellation(target: HlsDownloadMission) {
            if (target.isCancellationRequested) throw InterruptedIOException("HLS output copy was cancelled")
        }
    }
}
