package org.schabi.newpipe.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityReviewLinkTest {
    @Test
    fun onlyExplicitHttpsDestinationsWithoutIdentifiersAreAccepted() {
        assertEquals(
            "https://reviews.example.org/",
            CommunityReviewLink.configuredUrl(" https://reviews.example.org/ ")
        )
        listOf(
            "",
            " ",
            "http://reviews.example.org/",
            "javascript:alert(1)",
            "https://user:secret@reviews.example.org/",
            "https://reviews.example.org/?device=123",
            "https://reviews.example.org/#identity",
            "https:///missing-host",
            "not a URL"
        ).forEach { value -> assertNull(value, CommunityReviewLink.configuredUrl(value)) }
    }
}
