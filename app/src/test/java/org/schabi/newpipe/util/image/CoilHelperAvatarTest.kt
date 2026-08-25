package org.schabi.newpipe.util.image

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

class CoilHelperAvatarTest {
    @After
    fun restoreImagePreference() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun avatarCandidatesPreferConfiguredImageAndKeepFallbacks() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        val images = listOf(
            Image("https://example.com/low.jpg", 64, 64, ResolutionLevel.LOW),
            Image("https://example.com/high.jpg", 512, 512, ResolutionLevel.HIGH),
            Image("https://example.com/medium.jpg", 250, 250, ResolutionLevel.MEDIUM),
            Image("https://example.com/medium.jpg", 250, 250, ResolutionLevel.MEDIUM)
        )

        val candidates = avatarCandidateUrls(images)

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
    fun avatarCandidatesAreEmptyWhenImagesAreDisabled() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.NONE)

        val candidates = avatarCandidateUrls(
            listOf(Image("https://example.com/avatar.jpg", 250, 250, ResolutionLevel.MEDIUM))
        )

        assertEquals(emptyList<String>(), candidates)
    }
}
