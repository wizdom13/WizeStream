package org.schabi.newpipe.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredPlaybackLanguagesTest {
    @Test
    fun filtersTagsAndCaptionNamesWithoutHidingUnknownTracks() {
        val preferred = setOf("en", "fr")
        for (language in listOf("en", "en-US", "fr_CA", "English", "français", null, "")) {
            assertTrue(language, PreferredPlaybackLanguages.matches(language, preferred))
        }
        assertFalse(PreferredPlaybackLanguages.matches("de-DE", preferred))
        assertFalse(PreferredPlaybackLanguages.matches("German", preferred))
        assertTrue(PreferredPlaybackLanguages.matches("de-DE", emptySet()))
    }
}
