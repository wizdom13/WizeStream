package org.schabi.newpipe.database.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList

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
}
