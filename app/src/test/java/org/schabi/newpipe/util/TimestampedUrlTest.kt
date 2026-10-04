package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class TimestampedUrlTest {
    @Test
    fun replacesOldTimeWithoutDroppingOtherParametersOrFragments() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc&list=xyz&t=42#details",
            TimestampedUrl.atPosition("https://www.youtube.com/watch?v=abc&t=12&list=xyz&start=3#details", ServiceList.YouTube.serviceId, 42_999, false)
        )
        assertEquals("https://youtu.be/abc?t=0", TimestampedUrl.atPosition("https://youtu.be/abc", ServiceList.YouTube.serviceId, 0, false))
    }

    @Test
    fun liveUnknownPositionAndUnsupportedServicesKeepTheirOriginalLinks() {
        val url = "https://example.com/video"
        assertEquals(url, TimestampedUrl.atPosition(url, ServiceList.YouTube.serviceId, 42000, true))
        assertEquals(url, TimestampedUrl.atPosition(url, ServiceList.YouTube.serviceId, -1, false))
        assertEquals(url, TimestampedUrl.atPosition(url, ServiceList.SoundCloud.serviceId, 42000, false))
    }
}
