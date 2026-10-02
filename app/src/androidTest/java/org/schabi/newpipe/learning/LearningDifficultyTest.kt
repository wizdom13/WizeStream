package org.schabi.newpipe.learning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningDifficultyTest {
    @Test
    fun ratingsAreIsolatedByProfileServiceAndUrlAndCanBeCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = "difficulty-test-${java.util.UUID.randomUUID()}"
        val url = "https://example.com/lesson"
        try {
            LearningDifficulty.setRating(context, profile, 0, url, 3)
            assertEquals(3, LearningDifficulty.rating(context, profile, 0, url))
            assertEquals(0, LearningDifficulty.rating(context, "$profile-other", 0, url))
            assertEquals(0, LearningDifficulty.rating(context, profile, 1, url))
            assertEquals(0, LearningDifficulty.rating(context, profile, 0, "$url-other"))
            LearningDifficulty.setRating(context, profile, 0, url, 1)
            assertEquals(1, LearningDifficulty.rating(context, profile, 0, url))
        } finally {
            LearningDifficulty.setRating(context, profile, 0, url, 0)
        }
        assertEquals(0, LearningDifficulty.rating(context, profile, 0, url))
    }
}
