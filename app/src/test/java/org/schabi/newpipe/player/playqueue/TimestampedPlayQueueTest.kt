package org.schabi.newpipe.player.playqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class TimestampedPlayQueueTest {
    private val youtube = ServiceList.YouTube.serviceId

    @Test
    fun handlesBothReportedLinksAndUnitTimestamps() {
        for (url in listOf(
            "https://youtu.be/YE7VzlLtp-4?t=120",
            "https://www.youtube.com/watch?v=YE7VzlLtp-4&t=120",
            "https://youtu.be/YE7VzlLtp-4?t=2m",
            "https://youtu.be/YE7VzlLtp-4#t=120",
            "https://youtu.be/YE7VzlLtp-4?start=120",
            "https://youtu.be/YE7VzlLtp-4?time_continue=120"
        )) {
            assertEquals(url, 120_000L, TimestampedPlayQueue.positionMillis(youtube, url))
        }
        assertEquals(3_723_000L, TimestampedPlayQueue.positionMillis(youtube, "https://youtu.be/id?t=1h2m3s"))
    }

    @Test
    fun explicitZeroIsDifferentFromMissingOrInvalidPosition() {
        assertEquals(0L, TimestampedPlayQueue.positionMillis(youtube, "https://youtu.be/id?t=0"))
        for (value in listOf("", "-1", "1.5", "123garbage", "9223372036854775807", "999999999999999999999h", "%ZZ")) {
            assertNull(TimestampedPlayQueue.positionMillis(youtube, "https://youtu.be/id?t=$value"))
        }
        assertNull(TimestampedPlayQueue.positionMillis(youtube, "https://youtu.be/id"))
        assertNull(TimestampedPlayQueue.positionMillis(ServiceList.SoundCloud.serviceId, "https://soundcloud.com/x?t=12"))
    }

    @Test
    fun queueRetainsExplicitPositionWithoutMutatingCachedMetadata() {
        val info = Mockito.mock(StreamInfo::class.java)
        Mockito.`when`(info.serviceId).thenReturn(youtube)
        Mockito.`when`(info.url).thenReturn("https://www.youtube.com/watch?v=YE7VzlLtp-4")
        Mockito.`when`(info.name).thenReturn("Video")
        Mockito.`when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
        Mockito.`when`(info.startPosition).thenReturn(45L)
        val timed = TimestampedPlayQueue.create(info, "https://youtu.be/YE7VzlLtp-4?t=120")
        assertEquals(120_000L, timed.item!!.recoveryPosition)
        assertEquals(0L, TimestampedPlayQueue.create(info, "https://youtu.be/YE7VzlLtp-4?t=0").item!!.recoveryPosition)
        assertEquals(45_000L, TimestampedPlayQueue.create(info, info.url).item!!.recoveryPosition)
        Mockito.verify(info, Mockito.never()).startPosition = Mockito.anyLong()
    }
}
