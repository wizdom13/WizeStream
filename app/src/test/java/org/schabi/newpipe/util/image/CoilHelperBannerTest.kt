package org.schabi.newpipe.util.image

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

class CoilHelperBannerTest {
    @After
    fun restoreImagePreference() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun bannerCandidatesPreferConfiguredImageAndKeepFallbacks() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        val images = listOf(
            Image("https://example.com/low.jpg", 320, 80, ResolutionLevel.LOW),
            Image("https://example.com/high.jpg", 1920, 480, ResolutionLevel.HIGH),
            Image("https://example.com/medium.jpg", 1000, 250, ResolutionLevel.MEDIUM),
            Image("https://example.com/medium.jpg", 1000, 250, ResolutionLevel.MEDIUM)
        )

        val candidates = bannerCandidateUrls(images)

        assertEquals("https://example.com/medium.jpg", candidates.first())
        assertEquals(3, candidates.size)
        assertEquals(
            setOf(
                "https://example.com/low.jpg",
                "https://example.com/high.jpg",
                "https://example.com/medium.jpg"
            ),
            candidates.toSet()
        )
    }

    @Test
    fun bannerCandidatesAreEmptyWhenImagesAreDisabled() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.NONE)

        val candidates = bannerCandidateUrls(
            listOf(Image("https://example.com/banner.jpg", 1000, 250, ResolutionLevel.MEDIUM))
        )

        assertEquals(emptyList<String>(), candidates)
    }
}
