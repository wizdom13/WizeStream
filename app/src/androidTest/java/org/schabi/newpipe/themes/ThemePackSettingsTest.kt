package org.schabi.newpipe.themes

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.ThemePackSettingsFragment

@RunWith(AndroidJUnit4::class)
class ThemePackSettingsTest {
    @Test
    fun embeddedAssetPreviewSurvivesRecreationAndOnlyAppliesAfterConfirmation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.getString(ThemePacks.KEY, null)
        prefs.edit().remove(ThemePacks.KEY).commit()
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = ThemePackSettingsFragment()
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.settings_fragment_holder, fragment, "theme-packs-test").commitNow()
                    fragment.findPreference<Preference>("theme_pack_presets")!!.performClick()
                }
                onView(withText("Glass Record")).inRoot(isDialog()).perform(click())
                onView(withText(R.string.theme_pack_preview)).inRoot(isDialog()).check(matches(isDisplayed()))
                assertNull(ThemePacks.active(context))
                onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
                assertNull(ThemePacks.active(context))
                scenario.onActivity { activity ->
                    val fragment = activity.supportFragmentManager.findFragmentByTag("theme-packs-test") as ThemePackSettingsFragment
                    fragment.findPreference<Preference>("theme_pack_presets")!!.performClick()
                }
                onView(withText("Glass Record")).inRoot(isDialog()).perform(click())
                scenario.recreate()
                onView(withText(R.string.theme_pack_preview)).inRoot(isDialog()).check(matches(isDisplayed()))
                assertNull(ThemePacks.active(context))
                onView(withText(R.string.theme_pack_apply)).inRoot(isDialog()).perform(click())
                assertEquals("Glass Record", ThemePacks.active(context)?.name)
                assertEquals(4, ThemePacks.active(context)?.assets?.size)
                assertEquals(2, ThemePacks.active(context)?.version)
            }
        } finally {
            prefs.edit().apply {
                if (original == null) remove(ThemePacks.KEY) else putString(ThemePacks.KEY, original)
            }.commit()
        }
    }
}
