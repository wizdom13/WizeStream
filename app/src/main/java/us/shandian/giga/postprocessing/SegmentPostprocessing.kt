package us.shandian.giga.postprocessing

import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.schabi.newpipe.App
import org.schabi.newpipe.download.DownloadSegments
import org.schabi.newpipe.streams.io.SharpStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission

/** Trims a private working copy, leaving the downloaded source intact until export succeeds. */
internal class SegmentPostprocessing : Postprocessing(false, false, ALGORITHM_SEGMENTS) {
    override fun process(out: SharpStream?, vararg sources: SharpStream?): Int {
        val target = mission
        val output = temporalFile ?: throw IOException("Segment export has no temporary directory")
        val input = File(output.path + ".input")
        val normalized = DownloadSegments.normalize(DownloadSegments.decode(getArgumentAt(0, "")), streamInfo.duration * 1000)
        require(normalized.isNotEmpty())
        try {
            target.storage.stream.use { source ->
                input.outputStream().use { destination ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        throwIfCancellationRequested()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            val algorithm = getArgumentAt(1, "")
            if (algorithm.isNotEmpty()) {
                val originalArgs = mutableListOf<String>()
                var index = 2
                while (true) originalArgs.add(getArgumentAt(index++, null) ?: break)
                val delegate = getAlgorithm(algorithm, originalArgs.toTypedArray(), streamInfo)
                val staged = DownloadMission(target.urls, StoredFileHelper(App.instance, null, Uri.fromFile(input), ""), target.kind, delegate)
                staged.offsets = target.offsets.clone()
                staged.nearLength = target.nearLength
                delegate.setTemporalDir(output.parentFile!!)
                try {
                    delegate.run(staged)
                    if (staged.errCode != DownloadMission.ERROR_NOTHING) throw IOException("Preparing segment source failed", staged.errObject)
                } finally {
                    delegate.cleanupTemporalDir()
                }
            }
            val edits = normalized.map { range ->
                EditedMediaItem.Builder(
                    MediaItem.Builder().setUri(Uri.fromFile(input))
                        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(range.startMs).setEndPositionMs(range.endMs).build()).build()
                ).setRemoveVideo(target.kind == 'a').build()
            }
            val sequence = if (target.kind == 'a') EditedMediaItemSequence.withAudioFrom(edits) else EditedMediaItemSequence.withAudioAndVideoFrom(edits)
            export(Composition.Builder(sequence).build(), output)
            throwIfCancellationRequested()
            // A failed final write requires a fresh download, as with in-place muxing.
            worksOnSameFile = true
            output.inputStream().use { source ->
                target.storage.stream.use { destination ->
                    destination.setLength(0)
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        throwIfCancellationRequested()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            target.length = target.storage.length()
            target.done = target.length
            return OK_RESULT.toInt()
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun export(composition: Composition, output: File) {
        val thread = HandlerThread("segment-export").apply { start() }
        val handler = Handler(thread.looper)
        val done = CountDownLatch(1)
        val failure = AtomicReference<Exception>()
        var transformer: Transformer? = null
        handler.post {
            try {
                transformer = Transformer.Builder(App.instance).setLooper(thread.looper)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            done.countDown()
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            failure.set(exportException)
                            done.countDown()
                        }
                    }).build()
                transformer!!.start(composition, output.path)
            } catch (exception: Exception) {
                failure.set(exception)
                done.countDown()
            }
        }
        try {
            while (!done.await(250, TimeUnit.MILLISECONDS)) throwIfCancellationRequested()
            failure.get()?.let { throw IOException("Segment export failed", it) }
        } finally {
            val released = CountDownLatch(1)
            handler.post {
                try {
                    transformer?.cancel()
                } finally {
                    released.countDown()
                    thread.quitSafely()
                }
            }
            // Export must release file handles before the working files are removed.
            val interrupted = Thread.interrupted()
            try {
                released.await(10, TimeUnit.SECONDS)
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
    }
}
