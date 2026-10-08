package org.schabi.newpipe.fragments.list.search

import android.app.Application
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SearchFilterPresetsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun persistsUpdatesAndDeletesNamedFilters() {
        val store = SearchFilterPresets(context, 0, false)
        val preset = SearchFilterPresets.Preset("Recent lectures", "videos", listOf("long_video"), "2 months", "")
        store.save(preset)
        assertEquals(listOf(preset), SearchFilterPresets(context, 0, false).read())
        val edited = preset.copy(after = "1 week")
        store.save(edited)
        assertEquals(listOf(edited), store.read())
        store.delete(preset.name)
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun durationSettingsRoundTripWithRelativeDates() {
        val store = SearchFilterPresets(context, 0, false)
        val preset = SearchFilterPresets.Preset("Long lectures", "videos", emptyList(), "2 months", "", 1, 54_000, 180_000)
        store.save(preset)
        assertEquals(listOf(preset), store.read())
    }

    @Test
    fun separatesServicesAndMusicMode() {
        SearchFilterPresets(context, 0, false).save(SearchFilterPresets.Preset("Videos", "videos", emptyList(), "", ""))
        assertTrue(SearchFilterPresets(context, 1, false).read().isEmpty())
        assertTrue(SearchFilterPresets(context, 0, true).read().isEmpty())
    }

    @Test
    fun malformedBackupDoesNotCrashFilterPopup() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        SearchFilterPresets(context, 0, false).save(SearchFilterPresets.Preset("Videos", "videos", emptyList(), "", ""))
        val key = prefs.all.keys.single { it.startsWith("search_filter_presets_") }
        prefs.edit().putString(key, "invalid json").commit()
        assertTrue(SearchFilterPresets(context, 0, false).read().isEmpty())
    }
}
