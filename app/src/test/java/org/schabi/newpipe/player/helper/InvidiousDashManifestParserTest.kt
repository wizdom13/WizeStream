package org.schabi.newpipe.player.helper

import android.app.Application
import android.net.Uri
import androidx.media3.common.PlaybackException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.datasource.InvidiousMediaResponse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InvidiousDashManifestParserTest {
    private val uri = Uri.parse("https://example.org/api/manifest/dash/id/live?local=true")
    private val representation = """
        <AdaptationSet mimeType="audio/mp4">
          <Representation id="audio" bandwidth="128000" codecs="mp4a.40.2">
            <BaseURL>https://example.org/audio.m4a</BaseURL>
          </Representation>
        </AdaptationSet>
    """.trimIndent()

    @Test
    fun validLiveManifestRetainsInstanceTimingAndMediaUrls() {
        val xml = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic"
                 availabilityStartTime="2026-10-08T18:00:00Z" minBufferTime="PT1S"
                 minimumUpdatePeriod="PT5S" timeShiftBufferDepth="PT120S">
              <Period start="PT0S">$representation</Period>
            </MPD>
        """.trimIndent()
        val parsed = parse(xml)
        assertTrue(parsed.dynamic)
        assertEquals(1791482400000L, parsed.availabilityStartTimeMs)
        assertEquals(5000L, parsed.minUpdatePeriodMs)
        assertEquals(120000L, parsed.timeShiftBufferDepthMs)
        assertEquals("https://example.org/audio.m4a", parsed.getPeriod(0).adaptationSets.single().representations.single().baseUrls.single().url)
    }

    @Test
    fun invalidDurationProducesAnInstanceError() {
        val failure = invalid(manifest("PTNaNS", representation))
        assertEquals(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, failure.reason)
        assertEquals(uri, failure.dataSpec.uri)
    }

    @Test
    fun emptyManifestProducesAnInstanceError() {
        invalid(manifest("PT10S", ""))
    }

    @Test(timeout = 10_000)
    fun malformedXmlProducesAnInstanceError() {
        invalid("<MPD><Period>")
    }

    @Test
    fun networkReadFailuresKeepTheirOriginalClassification() {
        val failure = IOException("Connection interrupted")
        val input = object : InputStream() {
            override fun read(): Int = throw failure
        }
        assertSame(failure, assertThrows(IOException::class.java) { InvidiousDashManifestParser().parse(uri, input) })
    }

    private fun parse(xml: String) = InvidiousDashManifestParser().parse(uri, ByteArrayInputStream(xml.toByteArray()))

    private fun invalid(xml: String) = assertThrows(InvidiousMediaResponse.InvalidResponseException::class.java) { parse(xml) }

    private fun manifest(duration: String, content: String) = """
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"
             mediaPresentationDuration="$duration" minBufferTime="PT1S">
          <Period start="PT0S">$content</Period>
        </MPD>
    """.trimIndent()
}
