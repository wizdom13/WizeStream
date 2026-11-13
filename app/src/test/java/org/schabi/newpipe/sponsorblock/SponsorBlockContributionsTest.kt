package org.schabi.newpipe.sponsorblock

import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParser
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.download.DownloadedStreamInfo
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.ui.SponsorBlockContributionMenu

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SponsorBlockContributionsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences("sponsorblock-contribution-identity", 0).edit().clear().commit()
    }

    @Test
    fun contributionIdentityIsStablePrivateAndAbsentFromExportedPreferences() {
        val id = SponsorBlockContributions.identity(context)
        assertTrue(SponsorBlockContributions.validIdentity(id))
        assertEquals(id, SponsorBlockContributions.identity(context))
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context).all.values.contains(id))
        assertFalse(SponsorBlockContributions.setIdentity(context, "short"))
        assertEquals(id, SponsorBlockContributions.identity(context))
    }

    @Test
    fun contributionsRequireExplicitOptInAndAnArchivedOrOrdinaryYouTubeVideo() {
        val info = StreamInfo(ServiceList.YouTube.serviceId, "dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Video").apply {
            duration = 100
            streamType = StreamType.VIDEO_STREAM
        }
        assertFalse(SponsorBlockContributions.eligible(context, info))
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sponsor_block_contributions", true).commit()
        assertTrue(SponsorBlockContributions.eligible(context, info))
        info.streamType = StreamType.LIVE_STREAM
        assertFalse(SponsorBlockContributions.eligible(context, info))
        info.streamType = StreamType.POST_LIVE_STREAM
        assertTrue(SponsorBlockContributions.eligible(context, info))
    }

    @Test
    fun downloadedAndDirectFilesCannotSubmitAgainstAnUnverifiedYouTubeTimeline() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sponsor_block_contributions", true).commit()
        val downloaded = DownloadedStreamInfo("content://downloads/1", 0, "https://www.youtube.com/watch?v=$VIDEO", "Copy").apply {
            duration = 100
            streamType = StreamType.VIDEO_STREAM
        }
        assertFalse(SponsorBlockContributions.eligible(context, downloaded))
        val direct = StreamInfo(0, VIDEO, "content://downloads/2", "File").apply {
            duration = 100
            streamType = StreamType.VIDEO_STREAM
        }
        assertFalse(SponsorBlockContributions.eligible(context, direct))
    }

    @Test
    fun draftsRejectReversedOutOfRangeAndNonFiniteTimesBeforeCreatingAnyRequest() {
        listOf(
            ContributionDraft("sponsor", 10.0, 5.0, 100.0),
            ContributionDraft("sponsor", -1.0, 5.0, 100.0),
            ContributionDraft("sponsor", 1.0, 101.0, 100.0),
            ContributionDraft("sponsor", 1.0, Double.NaN, 100.0),
            ContributionDraft("sponsor", 1.0, 2.0, Double.POSITIVE_INFINITY),
            ContributionDraft("unknown", 1.0, 2.0, 100.0)
        ).forEach { draft -> assertThrows(IllegalArgumentException::class.java) { SponsorBlockContributions.submitRequest(VIDEO, USER, draft) } }
    }

    @Test
    fun submissionSendsSecondsAndIdentityInTheJsonBody() {
        val request = SponsorBlockContributions.submitRequest(VIDEO, USER, ContributionDraft("sponsor", 12.5, 19.25, 100.0))
        assertEquals("POST", request.method)
        assertEquals("https://sponsor.ajay.app/api/skipSegments", request.url.toString())
        val buffer = Buffer()
        requireNotNull(request.body).writeTo(buffer)
        val json = JsonParser.`object`().from(buffer.readUtf8())
        assertEquals(USER, json.getString("userID"))
        assertEquals("WizeStream", json.getString("userAgent"))
        assertEquals(100.0, json.getDouble("videoDuration"), 0.0)
        val segment = json.getArray("segments").getObject(0)
        assertEquals("skip", segment.getString("actionType"))
        assertEquals(12.5, segment.getArray("segment").getDouble(0), 0.0)
        assertEquals(19.25, segment.getArray("segment").getDouble(1), 0.0)
        val point = SponsorBlockContributions.submitRequest(VIDEO, USER, ContributionDraft("poi_highlight", 5.0, 5.0, 100.0))
        assertEquals("POST", point.method)
    }

    @Test
    fun votingSupportsUpDownAndUndoAndRejectsOtherScores() {
        listOf(0, 1, 20).forEach { score ->
            val request = SponsorBlockContributions.voteRequest(UUID, USER, score)
            assertEquals("POST", request.method)
            assertEquals(UUID, request.url.queryParameter("UUID"))
            assertEquals(score.toString(), request.url.queryParameter("type"))
        }
        assertThrows(IllegalArgumentException::class.java) { SponsorBlockContributions.voteRequest(UUID, USER, 2) }
        assertThrows(IllegalArgumentException::class.java) { SponsorBlockContributions.voteRequest("foreign-id", USER, 1) }
    }

    @Test
    fun voteChoicesOnlyIncludeValidatedSegmentsForTheRequestedVideo() {
        val json = """[{"videoID":"another","segments":[{"UUID":"$UUID","category":"sponsor","segment":[0,4]}]},{"videoID":"$VIDEO","segments":[{"UUID":"$UUID","category":"sponsor","segment":[12.5,19.25]},{"UUID":"bad","category":"sponsor","segment":[0,4]}]}]"""
        val segment = SponsorBlockContributions.parseSegments(json, VIDEO).single()
        assertEquals(UUID, segment.uuid)
        assertEquals(12.5, segment.start, 0.0)
        assertEquals(19.25, segment.end, 0.0)
    }

    @Test
    fun timestampInputAcceptsFractionalSecondsAndClockNotation() {
        assertEquals(90.5, SponsorBlockContributionMenu.parseTimestamp("90.5")!!, 0.0)
        assertEquals(62.5, SponsorBlockContributionMenu.parseTimestamp("1:02.5")!!, 0.0)
        assertEquals(3_723.25, SponsorBlockContributionMenu.parseTimestamp("1:02:03.25")!!, 0.0)
        listOf("", "-1", "1:60", "NaN", "Infinity", "1.5:20", "1:2:3:4").forEach {
            assertEquals(null, SponsorBlockContributionMenu.parseTimestamp(it))
        }
    }

    companion object {
        private const val VIDEO = "dQw4w9WgXcQ"
        private val USER = "a".repeat(64)
        private val UUID = "b".repeat(64)
    }
}
