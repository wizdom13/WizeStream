package org.schabi.newpipe.learning

import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningDifficultyTest {
    @Test
    fun legacyRatingsMoveToBackedUpPreferencesAndRestoreWithoutStaleMigration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = context.getSharedPreferences("difficulty-legacy-test", Context.MODE_PRIVATE)
        val portable = context.getSharedPreferences("difficulty-portable-test", Context.MODE_PRIVATE)
        val key = LearningDifficulty.key("00000000-0000-0000-0000-000000000000", 0, "https://example.com/lesson")
        try {
            legacy.edit().clear().putInt(key.removePrefix(LearningDifficulty.PREFIX), 3).commit()
            portable.edit().clear().putBoolean("learning_difficulty_enabled", true).commit()
            LearningDifficulty.migrate(legacy, portable)
            assertEquals(3, portable.getInt(key, 0))
            assertEquals("local", portable.getString(LearningDifficulty.MODE_KEY, null))
            assertEquals(0, legacy.all.size)
            val backup = portable.all.toMap()
            portable.edit().clear().commit()
            val manager = org.schabi.newpipe.settings.export.ImportExportManager(
                org.schabi.newpipe.settings.export.BackupFileLocator(context)
            )
            manager.replacePreferences(portable, backup)
            legacy.edit().putInt(key.removePrefix(LearningDifficulty.PREFIX), 1).commit()
            LearningDifficulty.migrate(legacy, portable)
            assertEquals(3, portable.getInt(key, 0))
        } finally {
            legacy.edit().clear().commit()
            portable.edit().clear().commit()
        }
    }

    @Test
    fun descriptionsWithoutAStreamDoNotAddDifficultyControls() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val layout = LinearLayout(context)
            LearningDifficulty.addDescriptionButton(layout, 0, null)
            LearningDifficulty.addDescriptionButton(layout, 0, "")
            LearningDifficulty.addDescriptionButton(layout, 0, "  ")
            assertEquals(0, layout.childCount)
        }
    }

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
