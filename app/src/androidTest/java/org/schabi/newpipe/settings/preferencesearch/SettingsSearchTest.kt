package org.schabi.newpipe.settings.preferencesearch

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.AppearanceSettingsFragment
import org.schabi.newpipe.settings.MainSettingsFragment
import org.schabi.newpipe.settings.NotificationsSettingsFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.SettingsResourceRegistry
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig
import org.schabi.newpipe.util.DeviceUtils

class SettingsSearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun indexIncludesDynamicPreferencesAndEveryVisibleLanguageChoice() {
        val searcher = SettingsSearchIndex.build(context)
        val caption = searcher.searchFor(context.getString(R.string.caption_auto_translate_title))
            .first { it.key == context.getString(R.string.caption_auto_translate_key) }
        assertEquals(R.xml.video_audio_settings, caption.searchIndexItemResId)
        val language = searcher.searchFor(context.getString(R.string.caption_translation_language_title))
            .first { it.key == context.getString(R.string.caption_translation_language_key) }
        assertFalse(language.entries.isEmpty())
        for (category in SponsorBlockCategoryConfig.ALL) {
            assertTrue(
                searcher.searchFor(context.getString(category.titleResId)).any {
                    it.key == context.getString(category.enabledKeyResId) &&
                        it.searchIndexItemResId == R.xml.sponsor_block_categories_settings
                }
            )
        }
        assertTrue(
            searcher.searchFor(context.getString(R.string.settings_search_notification_actions)).any {
                it.key == "notification_actions"
            }
        )
        assertEquals(
            NotificationsSettingsFragment::class.java,
            SettingsResourceRegistry.getInstance().getFragmentClass(R.xml.notifications_settings)
        )
    }

    @Test
    fun indexUsesLocalizedResourcesAndOmitsDeviceInapplicableOptions() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.FRENCH)
        }
        val localized = context.createConfigurationContext(configuration)
        val searcher = SettingsSearchIndex.build(localized)
        val languageResults = searcher.searchFor(localized.getString(R.string.app_language_title))
        val expected = if (Build.VERSION.SDK_INT >= 33) {
            R.string.app_language_android_13_and_up_key
        } else {
            R.string.app_language_key
        }
        val excluded = if (Build.VERSION.SDK_INT >= 33) {
            R.string.app_language_key
        } else {
            R.string.app_language_android_13_and_up_key
        }
        assertTrue(languageResults.any { it.key == localized.getString(expected) })
        assertFalse(languageResults.any { it.key == localized.getString(excluded) })
        val grid = searcher.searchFor(localized.getString(R.string.grid_columns_title))
        assertEquals(DeviceUtils.isTablet(localized), grid.any { it.key == localized.getString(R.string.grid_columns_key) })
        val parsed = PreferenceParser(localized, PreferenceSearchConfiguration()).parse(R.xml.appearance_settings)
        assertEquals(
            localized.getString(R.string.list_view_mode),
            parsed.first { it.key == localized.getString(R.string.list_view_mode_key) }.title
        )
        assertTrue(parsed.none { it.key == "general_preferences" })
    }

    @Test
    fun searchEntryResultsBackRotationAndClearWorkTogether() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val main = current(activity) as MainSettingsFragment
                val entry = main.findPreference<Preference>("settings_search")!!
                entry.onPreferenceClickListener!!.onPreferenceClick(entry)
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(current(activity) is PreferenceSearchFragment)
                query(activity, activity.getString(R.string.list_view_mode))
            }
            idle()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.searchResults)
                assertTrue(list.adapter!!.itemCount > 0)
            }
            scenario.recreate()
            idle()
            scenario.onActivity { activity ->
                assertTrue(current(activity) is PreferenceSearchFragment)
                assertEquals(activity.getString(R.string.list_view_mode), input(activity).text.toString())
                val list = activity.findViewById<RecyclerView>(R.id.searchResults)
                assertTrue(list.adapter!!.itemCount > 0)
                val key = activity.getString(R.string.list_view_mode_key)
                val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
                val before = preferences.getString(key, activity.getString(R.string.list_view_mode_value))
                // Click the real rendered first result, not just the listener.
                val row = list.findViewHolderForAdapterPosition(0)
                assertNotNull(row)
                row!!.itemView.performClick()
                activity.supportFragmentManager.executePendingTransactions()
                val destination = current(activity) as AppearanceSettingsFragment
                assertEquals(key, destination.requireArguments().getString(PreferenceSearchResultHighlighter.ARG_KEY))
                assertNotNull(destination.findPreference<Preference>(key))
                assertEquals(before, preferences.getString(key, activity.getString(R.string.list_view_mode_value)))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.toolbar_search_container).visibility)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(current(activity) is AppearanceSettingsFragment)
                activity.onBackPressedDispatcher.onBackPressed()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(current(activity) is PreferenceSearchFragment)
                assertEquals(activity.getString(R.string.list_view_mode), input(activity).text.toString())
                activity.findViewById<View>(R.id.toolbar_search_clear).performClick()
                input(activity).onEditorAction(EditorInfo.IME_ACTION_SEARCH)
                assertEquals("", input(activity).text.toString())
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty_state_view).visibility)
                query(activity, "xzyq987654")
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty_state_view).visibility)
                activity.onBackPressedDispatcher.onBackPressed()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(current(activity) is MainSettingsFragment)
            }
        }
    }

    @Test
    fun toolbarSearchIsAvailableInCategoriesAndReturnsToItsOrigin() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val main = current(activity) as MainSettingsFragment
                val destination = Preference(activity).apply {
                    fragment = AppearanceSettingsFragment::class.java.name
                }
                activity.onPreferenceStartFragment(main, destination)
                activity.supportFragmentManager.executePendingTransactions()
            }
            idle()
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
                val search = toolbar.menu.findItem(R.id.action_search)
                assertNotNull(search)
                assertTrue(search.isVisible)
                activity.onOptionsItemSelected(search)
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(current(activity) is PreferenceSearchFragment)
                activity.onBackPressedDispatcher.onBackPressed()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(current(activity) is AppearanceSettingsFragment)
            }
        }
    }

    @Test
    fun selectingAnOffscreenOptionScrollsItIntoView() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openSettingsSearch()
                activity.supportFragmentManager.executePendingTransactions()
                val item = SettingsSearchIndex.build(activity)
                    .searchFor(activity.getString(R.string.bottom_navigation_labels_title))
                    .first { it.key == activity.getString(R.string.bottom_navigation_labels_key) }
                activity.onSearchResultClicked(item)
                activity.supportFragmentManager.executePendingTransactions()
            }
            idle()
            scenario.onActivity { activity ->
                val destination = current(activity) as AppearanceSettingsFragment
                val preference = destination.findPreference<Preference>(activity.getString(R.string.bottom_navigation_labels_key))!!
                val list = destination.listView
                val position = (list.adapter as PreferenceGroup.PreferencePositionCallback)
                    .getPreferenceAdapterPosition(preference)
                assertTrue(position != RecyclerView.NO_POSITION)
                assertNotNull("The matching option should be visible without manual scrolling", list.findViewHolderForAdapterPosition(position))
                assertTrue(activity.supportFragmentManager.fragments.none { it is androidx.fragment.app.DialogFragment })
                // Reopen the existing search instead of stacking another search fragment.
                activity.openSettingsSearch()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(current(activity) is PreferenceSearchFragment)
                assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
            }
        }
    }

    private fun current(activity: SettingsActivity) = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder)

    private fun input(activity: SettingsActivity) = activity.findViewById<EditText>(R.id.toolbar_search_edit_text)

    private fun query(activity: SettingsActivity, text: String) {
        input(activity).setText(text)
        input(activity).onEditorAction(EditorInfo.IME_ACTION_SEARCH)
    }

    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
