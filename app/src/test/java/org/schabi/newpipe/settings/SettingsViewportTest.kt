/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.App
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewportTest {
    private lateinit var appInstance: MockedStatic<App>

    @Before
    fun provideApplicationForDeviceDetection() {
        // JVM tests use a plain Application to avoid starting services and workers.
        val app = mock(App::class.java)
        `when`(app.packageManager).thenReturn(RuntimeEnvironment.getApplication().packageManager)
        appInstance = mockStatic(App::class.java)
        // Settings' Java callers use the static bridge rather than Kotlin's companion getter.
        appInstance.`when`<App> { App::class.java.getMethod("getInstance").invoke(null) }.thenReturn(app)
    }

    @After
    fun releaseApplicationStub() {
        appInstance.close()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun mainSettingsEndRowRemainsAboveNavigationButtons() = withSettings(360, 800) {
        assertLearningModeReachable()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun sponsorBlockEndRowRemainsAboveNavigationButtons() = withSettings(360, 800) {
        assertReplaceThumbnailsReachable()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun shortLandscapeKeepsBothEndRowsInsideTheSafeViewport() = withSettings(640, 360) {
        assertLearningModeReachable()
        assertReplaceThumbnailsReachable()
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp-land")
    fun twoPaneSettingsKeepBothListsInsideTheSafeViewport() = withSettings(1000, 700) {
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_navigation_holder).visibility)
        assertLearningModeReachable()
        assertReplaceThumbnailsReachable()
    }

    private fun withSettings(width: Int, height: Int, check: SettingsWindow.() -> Unit) {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            SettingsWindow(activity, width, height).apply {
                layout()
                check()
            }
        }
    }

    private class SettingsWindow(val activity: SettingsActivity, width: Int, height: Int) {
        private val appBar = activity.findViewById<AppBarLayout>(R.id.settings_app_bar)
        private val root = appBar.parent as ViewGroup
        private val widthPixels = dp(width)
        private val heightPixels = dp(height)
        private val safeInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, dp(24), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, dp(48)))
            .build()

        fun assertLearningModeReachable() {
            val categories = categories()
            // Learning Mode is the final category in releases; debug builds append Debug.
            categories.findPreference<Preference>(activity.getString(R.string.debug_pref_screen_key))?.let {
                categories.preferenceScreen.removePreference(it)
            }
            shadowOf(Looper.getMainLooper()).idle()
            layout()
            val list = categories.listView
            val adapter = list.adapter as PreferenceGroupAdapter
            assertEquals(LearningSettingsFragment::class.java.name, adapter.getItem(adapter.itemCount - 1)?.fragment)
            assertLastRowReachable(list, "Learning Mode")
        }

        fun assertReplaceThumbnailsReachable() {
            val destination = Preference(activity).apply {
                fragment = SponsorBlockSettingsFragment::class.java.name
            }
            assertTrue(activity.onPreferenceStartFragment(categories(), destination))
            activity.supportFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            layout()
            val sponsorBlock = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as SponsorBlockSettingsFragment
            val list = sponsorBlock.listView
            val adapter = list.adapter as PreferenceGroupAdapter
            assertEquals(activity.getString(R.string.dearrow_thumbnails_key), adapter.getItem(adapter.itemCount - 1)?.key)
            assertLastRowReachable(list, "Replace thumbnails")
        }

        fun layout() {
            ViewCompat.dispatchApplyWindowInsets(root, safeInsets)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, widthPixels, heightPixels)
            assertEquals(dp(24), root.paddingTop)
            assertEquals(dp(48), root.paddingBottom)
        }

        private fun categories() = (
            activity.supportFragmentManager.findFragmentById(R.id.settings_navigation_holder)
                ?: activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder)
            ) as MainSettingsFragment

        private fun assertLastRowReachable(list: RecyclerView, title: String) {
            val lastPosition = list.adapter!!.itemCount - 1
            // Search highlighting, focus, and restored scroll positions can reach the end
            // without a drag. Do not manually collapse the app bar to make this pass.
            list.scrollToPosition(lastPosition)
            layout()
            val row = list.findViewHolderForAdapterPosition(lastPosition)?.itemView
            assertNotNull("The final $title row must be bound after scrolling", row)
            assertFalse("The list must have reached its end", list.canScrollVertically(1))
            val bounds = Rect(0, 0, row!!.width, row.height)
            root.offsetDescendantRectToMyCoords(row, bounds)
            val safeBottom = root.height - root.paddingBottom
            println("Settings viewport $title: row=$bounds, safe bottom=$safeBottom, toolbar height=${appBar.height}, scroll range=${appBar.totalScrollRange}")
            assertTrue("$title has no usable height", bounds.height() > 0)
            assertTrue("$title overlaps the toolbar: $bounds, toolbar bottom ${appBar.bottom}", bounds.top >= appBar.bottom)
            assertTrue(
                "$title is below the safe viewport: $bounds, safe bottom $safeBottom, app bar scroll range ${appBar.totalScrollRange}",
                bounds.bottom <= safeBottom
            )
            assertTrue("$title is clipped horizontally: $bounds", bounds.left >= root.paddingLeft && bounds.right <= root.width - root.paddingRight)
        }

        private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    }
}
