package us.shandian.giga.postprocessing

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.App
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission

@RunWith(AndroidJUnit4::class)
class SegmentPostprocessingTest {
    @Test
    fun exportsTwoAudioSegmentsAsOnePlayableFile() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val directory = File(context.cacheDir, "segment-test-${System.nanoTime()}").apply { mkdirs() }
        val file = File(directory, "segments.m4a")
        val sampleRate = 44100
        val samples = sampleRate * 3
        val dataSize = samples * 2
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        bytes.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
        repeat(samples) { bytes.putShort((sin(it * 2.0 * Math.PI * 440 / sampleRate) * 16000).toInt().toShort()) }
        file.writeBytes(bytes.array())
        val info = StreamInfo(0, "sample", "https://example.com/sample", "Sample").apply { duration = 3 }
        val processor = Postprocessing.getAlgorithm(Postprocessing.ALGORITHM_SEGMENTS, arrayOf("500:1000,2000:2500", ""), info)
        val mission = DownloadMission(arrayOf(info.url), StoredFileHelper(context, null, Uri.fromFile(file), ""), 'a', processor)
        processor.setTemporalDir(directory)
        try {
            processor.run(mission)
            assertEquals(DownloadMission.ERROR_NOTHING, mission.errCode)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(file.path)
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Expected about one second, got $duration ms", duration in 900..1150)
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            } finally {
                metadata.release()
            }
        } finally {
            processor.cleanupTemporalDir()
            directory.deleteRecursively()
        }
    }
}
