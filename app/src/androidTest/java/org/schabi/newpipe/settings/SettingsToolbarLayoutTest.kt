/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchFragment

class SettingsToolbarLayoutTest {
    @Test
    fun compactToolbarRestoresSectionTitleAfterSearchAndRecreation() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity(::openSponsorBlock)
            idle()
            scenario.onActivity { activity ->
                assertCompactTitle(activity)
                val search = activity.findViewById<Toolbar>(R.id.toolbar).menu.findItem(R.id.action_search)
                assertNotNull(search)
                assertTrue(search.isVisible)
                assertTrue(activity.onOptionsItemSelected(search))
                activity.supportFragmentManager.executePendingTransactions()
            }
            idle()
            scenario.onActivity(::assertSearchVisible)
            scenario.recreate()
            idle()
            scenario.onActivity { activity ->
                assertSearchVisible(activity)
                activity.onBackPressedDispatcher.onBackPressed()
                activity.supportFragmentManager.executePendingTransactions()
            }
            idle()
            scenario.onActivity { activity ->
                assertTrue(current(activity) is SponsorBlockSettingsFragment)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.toolbar_search_container).visibility)
                assertCompactTitle(activity)
            }
        }
    }

    @Test
    fun finalPreferencesRemainAboveSystemNavigationWithoutCollapsingTheHeader() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val main = categories(activity)
                // Release builds end at Learning Mode rather than the debug-only category.
                main.findPreference<Preference>(activity.getString(R.string.debug_pref_screen_key))?.let {
                    main.preferenceScreen.removePreference(it)
                }
            }
            idle()
            scenario.onActivity { activity ->
                val list = categories(activity).listView
                val adapter = list.adapter as PreferenceGroupAdapter
                assertEquals(LearningSettingsFragment::class.java.name, adapter.getItem(adapter.itemCount - 1)?.fragment)
                list.scrollToPosition(adapter.itemCount - 1)
            }
            idle()
            scenario.onActivity { activity ->
                assertLastRowInsideSafeWindow(activity, categories(activity).listView)
                openSponsorBlock(activity)
            }
            idle()
            scenario.onActivity { activity ->
                val list = (current(activity) as SponsorBlockSettingsFragment).listView
                val adapter = list.adapter as PreferenceGroupAdapter
                assertEquals(activity.getString(R.string.dearrow_thumbnails_key), adapter.getItem(adapter.itemCount - 1)?.key)
                list.scrollToPosition(adapter.itemCount - 1)
            }
            idle()
            scenario.onActivity { activity ->
                assertLastRowInsideSafeWindow(activity, (current(activity) as SponsorBlockSettingsFragment).listView)
            }
        }
    }

    private fun assertCompactTitle(activity: SettingsActivity) {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val appBar = activity.findViewById<AppBarLayout>(R.id.settings_app_bar)
        val title = (current(activity) as BasePreferenceFragment).preferenceScreen.title.toString()
        assertEquals(title, toolbar.title?.toString())
        assertNotNull(toolbar.navigationIcon)
        val label = toolbar.children.filterIsInstance<TextView>().firstOrNull { it.text.toString() == title }
        assertNotNull("The section title should be drawn in the navigation toolbar", label)
        assertTrue(label!!.width > 0 && label.height > 0)
        assertTrue(label.top >= 0 && label.bottom <= toolbar.height)
        assertEquals("Settings should not reserve a second title row", toolbar.height, appBar.height)
        assertEquals("Scrolling settings must not change the header height", 0, appBar.totalScrollRange)
    }

    private fun assertSearchVisible(activity: SettingsActivity) {
        assertTrue(current(activity) is PreferenceSearchFragment)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.toolbar_search_container).visibility)
        assertTrue("The section title must make room for search", activity.findViewById<Toolbar>(R.id.toolbar).title.isNullOrEmpty())
    }

    private fun assertLastRowInsideSafeWindow(activity: SettingsActivity, list: RecyclerView) {
        val appBar = activity.findViewById<AppBarLayout>(R.id.settings_app_bar)
        val root = appBar.parent as ViewGroup
        val insets = ViewCompat.getRootWindowInsets(root)!!
        val navigation = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        assertTrue("Settings must reserve the system navigation area", root.paddingBottom >= navigation.bottom)
        val row = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)?.itemView
        assertNotNull("The final preference must be laid out after scrolling", row)
        assertFalse("The list must have reached its end", list.canScrollVertically(1))
        val bounds = Rect(0, 0, row!!.width, row.height)
        root.offsetDescendantRectToMyCoords(row, bounds)
        assertTrue("The complete preference must fit above system navigation: $bounds", bounds.bottom <= root.height - root.paddingBottom)
        assertTrue("The preference must not overlap the toolbar: $bounds", bounds.top >= appBar.bottom)
        val visible = Rect()
        assertTrue(row.getGlobalVisibleRect(visible))
        assertEquals("The final row must not be clipped by an ancestor", row.height, visible.height())
    }

    private fun openSponsorBlock(activity: SettingsActivity) {
        val destination = Preference(activity).apply { fragment = SponsorBlockSettingsFragment::class.java.name }
        assertTrue(activity.onPreferenceStartFragment(categories(activity), destination))
        activity.supportFragmentManager.executePendingTransactions()
    }

    private fun categories(activity: SettingsActivity) = (
        activity.supportFragmentManager.findFragmentById(R.id.settings_navigation_holder)
            ?: current(activity)
        ) as MainSettingsFragment

    private fun current(activity: SettingsActivity) = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder)

    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
