package org.schabi.newpipe.util

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class YouTubeRatingsPrivacyTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val previousFetchDislike = ServiceList.YouTube.isFetchDislike
    private val requests = mutableListOf<String>()

    @Before
    fun setup() {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                requests += request.url()
                val body = """{"likes":12,"dislikes":3,"viewCount":100}"""
                return Response(200, "OK", emptyMap(), body, body.toByteArray(), request.url())
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException()
        })
    }

    @After
    fun reset() {
        ServiceList.YouTube.setFetchDislike(previousFetchDislike)
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun disabledDislikesNeverContactTheServiceEvenForMissingLikesAndViews() {
        ServiceList.YouTube.setFetchDislike(false)
        val info = info()
        backfill(info)
        assertTrue(requests.isEmpty())
        assertEquals(-1L, info.likeCount)
        assertEquals(-1L, info.dislikeCount)
        assertEquals(-1L, info.viewCount)
    }

    @Test
    fun enabledDislikesStillBackfillMissingMetadataAndPreserveKnownCounts() {
        ServiceList.YouTube.setFetchDislike(true)
        val info = info().apply { likeCount = 20 }
        backfill(info)
        assertEquals(listOf("https://returnyoutubedislikeapi.com/votes?videoId=BaW_jenozKc"), requests)
        assertEquals(20L, info.likeCount)
        assertEquals(3L, info.dislikeCount)
        assertEquals(100L, info.viewCount)
    }

    private fun info() = StreamInfo(0, "BaW_jenozKc", "https://www.youtube.com/watch?v=BaW_jenozKc", "Video").apply {
        likeCount = -1
        dislikeCount = -1
        viewCount = -1
    }

    private fun backfill(info: StreamInfo) {
        ExtractorHelper::class.java.getDeclaredMethod(
            "backfillYouTubeRatingsAndViewCount",
            Int::class.javaPrimitiveType,
            StreamInfo::class.java
        ).apply { isAccessible = true }.invoke(null, ServiceList.YouTube.serviceId, info)
    }
}
