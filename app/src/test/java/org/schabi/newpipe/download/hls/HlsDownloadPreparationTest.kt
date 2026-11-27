/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = Application::class)
class HlsDownloadPreparationTest {
    @Test
    fun selectedHeightAndDefaultMatchingAudioProduceOnlyTwoKeysAndKeepSignedUrls() = withCache { cache, id ->
        val files = fixture()
        val source = MemoryFactory(files)
        val headers = hashMapOf("Referer" to "https://fixture.example/watch")
        val plan = HlsDownloadPreparation.prepare(cache, id, MASTER, headers, 720, source)
        headers["Referer"] = "changed"
        assertEquals(720, plan.height)
        assertEquals(true, plan.hasAudio)
        assertEquals(listOf(HlsDownloadStreamKey(0, 1), HlsDownloadStreamKey(1, 0)), plan.streamKeys)
        assertEquals("https://fixture.example/video/720.m3u8?token=video", plan.videoPlaylistUrl)
        assertEquals("https://fixture.example/audio/default.m3u8?token=audio", plan.audioPlaylistUrl)
        assertEquals("https://fixture.example/watch", plan.requestHeaders["Referer"])
        assertEquals(setOf(MASTER, plan.videoPlaylistUrl, plan.audioPlaylistUrl), source.requests.toSet())
        assertEquals(2, plan.toMediaItem().localConfiguration!!.streamKeys.size)
    }

    @Test
    fun unknownResolutionSelectsHighestCompatibleVariantInsteadOfUnsupportedHevc() = withCache { cache, id ->
        val files = fixture()
        files[MASTER] = files.getValue(MASTER) + "\n#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=3840x2160,CODECS=\"hvc1.1.6.L120,mp4a.40.2\"\nunsupported.m3u8\n"
        val plan = HlsDownloadPreparation.prepare(cache, id, MASTER, emptyMap(), upstreamFactory = MemoryFactory(files))
        assertEquals(1080, plan.height)
        assertEquals(HlsDownloadStreamKey(0, 2), plan.streamKeys.first())
    }

    @Test
    fun completeValidatedManifestsAreReadFromCacheOnResumeWithoutNetwork() = withCache { cache, id ->
        val source = MemoryFactory(fixture())
        val first = HlsDownloadPreparation.prepare(cache, id, MASTER, emptyMap(), 720, source)
        val noNetwork = MemoryFactory(emptyMap())
        val second = HlsDownloadPreparation.prepare(cache, id, MASTER, emptyMap(), 720, noNetwork)
        assertEquals(first, second)
        assertEquals(3, source.requests.size)
        assertTrue(noNetwork.requests.isEmpty())
    }

    @Test
    fun directFiniteMediaPlaylistHasNoMasterKeysAndDefersUnknownCodecInspection() = withCache { cache, id ->
        val plan = HlsDownloadPreparation.prepare(cache, id, DIRECT, emptyMap(), upstreamFactory = MemoryFactory(mapOf(DIRECT to media())))
        assertTrue(plan.streamKeys.isEmpty())
        assertNull(plan.hasAudio)
        assertEquals(4_000_000L, plan.durationUs)
        assertEquals(DIRECT, plan.manifestUrl)
    }

    @Test
    fun knownVideoOnlyMasterRecordsAbsenceOfAudio() = withCache { cache, id ->
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=320x180,CODECS=\"avc1.42e01e\"\nvideo.m3u8\n"
        val plan = HlsDownloadPreparation.prepare(cache, id, MASTER, emptyMap(), upstreamFactory = MemoryFactory(mapOf(MASTER to master, "https://fixture.example/video.m3u8" to media())))
        assertEquals(false, plan.hasAudio)
        assertEquals(1, plan.streamKeys.size)
    }

    @Test
    fun livePlaylistIsRejectedBeforeAnySegmentIsRead() = rejects(media().replace("#EXT-X-ENDLIST", ""))

    @Test
    fun aesAndDrmKeysAreRejectedBeforeAnyKeyOrSegmentRequest() {
        for (key in listOf(
            "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"",
            "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://fixture\",KEYFORMAT=\"com.apple.streamingkeydelivery\""
        )) {
            rejects(media().replace("#EXTINF:4.0,", "$key\n#EXTINF:4.0,"))
        }
    }

    @Test
    fun gapsDiscontinuitiesAndNonzeroStartOffsetAreRejected() {
        for (tag in listOf("#EXT-X-GAP", "#EXT-X-DISCONTINUITY", "#EXT-X-START:TIME-OFFSET=1.5")) {
            rejects(media().replace("#EXTINF:4.0,", "$tag\n#EXTINF:4.0,"))
        }
    }

    @Test
    fun changingInitializationSegmentsAreRejected() = rejects(
        """
        #EXTM3U
        #EXT-X-TARGETDURATION:2
        #EXT-X-MAP:URI="init1.mp4"
        #EXTINF:2.0,
        first.m4s
        #EXT-X-MAP:URI="init2.mp4"
        #EXTINF:2.0,
        second.m4s
        #EXT-X-ENDLIST
        """.trimIndent()
    )

    @Test
    fun declaredUnsupportedAudioOrMissingAudioGroupCannotSilentlyLoseSound() = withCache { cache, id ->
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=320x180,CODECS=\"avc1.42e01e,mp4a.40.2\",AUDIO=\"missing\"\nvideo.m3u8\n"
        assertThrows(UnsupportedHlsDownloadException::class.java) {
            HlsDownloadPreparation.prepare(cache, id, MASTER, emptyMap(), upstreamFactory = MemoryFactory(mapOf(MASTER to master, "https://fixture.example/video.m3u8" to media())))
        }
        assertFalse(HlsDownloadPreparation.supportedCodecs("avc1.42e01e,ac-3", true))
    }

    @Test
    fun oversizedManifestIsRejectedWithBoundedRead() = withCache { cache, id ->
        val factory = MemoryFactory(mapOf(DIRECT to "#EXTM3U\n".padEnd(HlsDownloadPreparation.MAX_MANIFEST_BYTES + 1, '#')))
        assertThrows(UnsupportedHlsDownloadException::class.java) {
            HlsDownloadPreparation.prepare(cache, id, DIRECT, emptyMap(), upstreamFactory = factory)
        }
        assertEquals(listOf(DIRECT), factory.requests)
    }

    @Test
    fun planRoundTripsThroughMissionSerializationWithoutAndroidRuntimeObjects() = withCache { cache, id ->
        val plan = HlsDownloadPreparation.prepare(cache, id, MASTER, mapOf("Authorization" to "fixture"), 720, MemoryFactory(fixture()))
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(plan) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as HlsDownloadPlan }
        assertEquals(plan, restored)
        assertEquals(plan.toMediaItem().localConfiguration!!.streamKeys, restored.toMediaItem().localConfiguration!!.streamKeys)
    }

    @Test
    fun offlineFactoryCannotFetchAMissingSegment() {
        withCache { cache, _ ->
            val source = HlsDownloadCache.offlineFactory(cache).createDataSource()
            try {
                assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("https://fixture.example/missing.ts"))) }
            } finally {
                source.close()
            }
        }
    }

    @Test
    fun resolutionAndResourceUrlValidationDoesNotInventOrStripMetadata() {
        assertEquals(720, HlsDownloadPreparation.desiredHeight("720p60"))
        assertNull(HlsDownloadPreparation.desiredHeight("Auto"))
        assertNull(HlsDownloadPreparation.desiredHeight("best"))
        assertNotNull(HlsDownloadPreparation.validateUrl("https://fixture.example/video?sig=abc%2Fdef"))
        for (url in listOf("file:///video.m3u8", "https://user:pass@fixture.example/video", "https://fixture.example/video#fragment")) {
            assertThrows(UnsupportedHlsDownloadException::class.java) { HlsDownloadPreparation.validateUrl(url) }
        }
    }

    private fun rejects(body: String) = withCache { cache, id ->
        val factory = MemoryFactory(mapOf(DIRECT to body))
        assertThrows(UnsupportedHlsDownloadException::class.java) {
            HlsDownloadPreparation.prepare(cache, id, DIRECT, emptyMap(), upstreamFactory = factory)
        }
        assertEquals(listOf(DIRECT), factory.requests)
    }

    private fun fixture(): MutableMap<String, String> = mutableMapOf(
        MASTER to """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="main",NAME="Default",DEFAULT=YES,AUTOSELECT=YES,URI="audio/default.m3u8?token=audio"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="main",NAME="Other",DEFAULT=NO,AUTOSELECT=NO,URI="audio/other.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2",AUDIO="main"
            video/360.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS="avc1.42e01e,mp4a.40.2",AUDIO="main"
            video/720.m3u8?token=video
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080,CODECS="avc1.42e01e,mp4a.40.2",AUDIO="main"
            video/1080.m3u8
        """.trimIndent(),
        "https://fixture.example/video/360.m3u8" to media(),
        "https://fixture.example/video/720.m3u8?token=video" to media(),
        "https://fixture.example/video/1080.m3u8" to media(),
        "https://fixture.example/audio/default.m3u8?token=audio" to media()
    )

    private fun media() = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:4.0,\nsegment.ts?token=segment\n#EXT-X-ENDLIST\n"

    private fun <T> withCache(action: (Cache, String) -> T): T {
        val context = RuntimeEnvironment.getApplication()
        val id = UUID.randomUUID().toString()
        val cache = HlsDownloadCache.open(context, id)
        try {
            return action(cache, id)
        } finally {
            cache.release()
            HlsDownloadCache.remove(context, id)
        }
    }

    private class MemoryFactory(private val files: Map<String, String>) : DataSource.Factory {
        val requests = mutableListOf<String>()

        override fun createDataSource(): DataSource = object : DataSource {
            private var delegate: ByteArrayDataSource? = null

            override fun addTransferListener(transferListener: TransferListener) = Unit

            override fun open(dataSpec: DataSpec): Long {
                val url = dataSpec.uri.toString()
                requests.add(url)
                val bytes = files[url]?.toByteArray() ?: throw IOException("Unexpected fixture request: $url")
                return ByteArrayDataSource(bytes).also { delegate = it }.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

            override fun getUri(): Uri? = delegate?.uri

            override fun close() {
                delegate?.close()
            }
        }
    }

    companion object {
        private const val MASTER = "https://fixture.example/master.m3u8?token=master"
        private const val DIRECT = "https://fixture.example/direct.m3u8?token=direct"
    }
}
