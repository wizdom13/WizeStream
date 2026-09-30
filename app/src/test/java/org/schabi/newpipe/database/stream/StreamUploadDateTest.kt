package org.schabi.newpipe.database.stream

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamType

class StreamUploadDateTest {
    private val youtube = SubscriptionEntity.YOUTUBE_SERVICE_ID

    @Test
    fun `legacy shorts fallback is not a source publication date`() {
        assertTrue(StreamUploadDate.isSynthetic(youtube, true, null))
        assertTrue(StreamUploadDate.isSynthetic(youtube, true, ""))
        assertTrue(StreamUploadDate.isSynthetic(youtube, true, "   "))
    }

    @Test
    fun `source relative dates exact dates and other services remain publication dates`() {
        assertFalse(StreamUploadDate.isSynthetic(youtube, true, "2 days ago"))
        assertFalse(StreamUploadDate.isSynthetic(youtube, false, null))
        assertFalse(StreamUploadDate.isSynthetic(youtube, null, null))
        assertFalse(StreamUploadDate.isSynthetic(ServiceList.SoundCloud.serviceId, true, null))
    }

    @Test
    fun `history conversion preserves source date provenance`() {
        val date = OffsetDateTime.parse("2026-09-27T00:00:00Z")
        val entity = StreamEntity(
            serviceId = youtube, url = "https://youtube.com/watch?v=video", title = "Video",
            streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Channel",
            uploadDate = date, isUploadDateApproximation = true, textualUploadDate = "3 days ago"
        )
        val item = StreamStatisticsEntry(entity, 0, 1, date, 1).toStreamInfoItem()

        assertEquals("3 days ago", item.textualUploadDate)
        assertEquals(date, item.uploadDate!!.offsetDateTime())
        assertFalse(StreamUploadDate.isSynthetic(item.serviceId, item.uploadDate!!.isApproximation, item.textualUploadDate))
    }
}
