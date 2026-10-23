package org.schabi.newpipe.error

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend

class InvidiousDirectRetryTest {
    @After
    fun resetBackend() = InvidiousBackend.configure(false, "")

    @Test
    fun retryIsOnlyOfferedForAnInvidiousYoutubeVideoAndDoesNotChangeThePolicy() {
        val youtube = ServiceList.YouTube.serviceId
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        assertFalse(InvidiousDirectRetry.isAvailable(youtube, url))
        InvidiousBackend.configure(true, "https://example.org")
        assertTrue(InvidiousDirectRetry.isAvailable(youtube, url))
        assertFalse(InvidiousDirectRetry.isAvailable(ServiceList.SoundCloud.serviceId, url))
        assertFalse(InvidiousDirectRetry.isAvailable(youtube, "https://example.org/video"))
        assertFalse(InvidiousDirectRetry.isAvailable(youtube, null))
        assertTrue(InvidiousBackend.isEnabled())
    }
}
