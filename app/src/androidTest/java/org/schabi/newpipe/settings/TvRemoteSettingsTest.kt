package org.schabi.newpipe.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.settings.preferencesearch.SettingsSearchIndex
import org.schabi.newpipe.util.TvRemoteAction
import org.schabi.newpipe.util.TvRemoteKeyDispatcher
import org.schabi.newpipe.util.TvRemoteKeys

class TvRemoteSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val keys = TvRemoteKeys(preferences)
    private lateinit var saved: Map<String, Any?>

    @Before
    fun saveAssignments() {
        saved = TvRemoteAction.entries.associate { it.preferenceKey to preferences.all[it.preferenceKey] }
        keys.reset()
    }

    @After
    fun restoreAssignments() {
        preferences.edit().apply {
            saved.forEach { (key, value) ->
                when (value) {
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                    else -> remove(key)
                }
            }
        }.commit()
    }

    @Test
    fun assignmentsPersistMoveRemoveAndResetWithoutChangingOtherPreferences() {
        val unrelated = preferences.all.filterKeys { it !in saved }
        keys.assign(TvRemoteAction.PLAY_PAUSE, KeyEvent.KEYCODE_PROG_RED)
        val reloaded = TvRemoteKeys(PreferenceManager.getDefaultSharedPreferences(context))
        assertEquals(TvRemoteAction.PLAY_PAUSE, reloaded.actionFor(KeyEvent.KEYCODE_PROG_RED))
        reloaded.assign(TvRemoteAction.SEARCH, KeyEvent.KEYCODE_PROG_RED)
        assertNull(keys.keyFor(TvRemoteAction.PLAY_PAUSE))
        assertEquals(TvRemoteAction.SEARCH, keys.actionFor(KeyEvent.KEYCODE_PROG_RED))
        keys.assign(TvRemoteAction.SEARCH, KeyEvent.KEYCODE_PROG_GREEN)
        assertNull(keys.actionFor(KeyEvent.KEYCODE_PROG_RED))
        keys.remove(TvRemoteAction.SEARCH)
        assertNull(keys.actionFor(KeyEvent.KEYCODE_PROG_GREEN))
        keys.assign(TvRemoteAction.HOME, KeyEvent.KEYCODE_1)
        keys.assign(TvRemoteAction.MUTE, KeyEvent.KEYCODE_MEDIA_STOP)
        keys.reset()
        assertTrue(TvRemoteAction.entries.all { keys.keyFor(it) == null })
        assertEquals(unrelated, preferences.all.filterKeys { it !in saved })
    }

    @Test
    fun malformedOrReservedImportedAssignmentsAreIgnoredAndSettingsAreSearchable() {
        preferences.edit()
            .putString(TvRemoteAction.PLAY_PAUSE.preferenceKey, "broken")
            .putInt(TvRemoteAction.SEARCH.preferenceKey, KeyEvent.KEYCODE_BACK)
            .apply()
        assertNull(keys.keyFor(TvRemoteAction.PLAY_PAUSE))
        assertNull(keys.actionFor(KeyEvent.KEYCODE_BACK))
        assertTrue(
            SettingsSearchIndex.build(context).searchFor(context.getString(R.string.remote_play_pause)).any {
                it.key == TvRemoteAction.PLAY_PAUSE.preferenceKey && it.searchIndexItemResId == R.xml.tv_remote_settings
            }
        )
    }

    @Test
    fun capturedButtonSurvivesRotationAndRequiresConfirmationBeforeReplacingAConflict() {
        keys.assign(TvRemoteAction.MUTE, KeyEvent.KEYCODE_PROG_RED)
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openSettings(activity)
                openCapture(activity, TvRemoteAction.SEARCH)
                val dialog = dialog(activity)
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                press(dialog, KeyEvent.KEYCODE_PROG_RED)
                assertTrue(dialog.findViewById<TextView>(android.R.id.message)!!.text.contains(context.getString(R.string.remote_mute)))
                assertEquals(TvRemoteAction.MUTE, keys.actionFor(KeyEvent.KEYCODE_PROG_RED))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val dialog = dialog(activity)
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            idle()
            scenario.onActivity { activity ->
                assertNull(keys.keyFor(TvRemoteAction.MUTE))
                assertEquals(TvRemoteAction.SEARCH, keys.actionFor(KeyEvent.KEYCODE_PROG_RED))
                val screen = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as TvRemoteSettingsFragment
                assertEquals(TvRemoteKeys.label(KeyEvent.KEYCODE_PROG_RED), screen.findPreference<Preference>(TvRemoteAction.SEARCH.preferenceKey)!!.summary)
                openCapture(activity, TvRemoteAction.SEARCH)
                press(dialog(activity), KeyEvent.KEYCODE_PROG_GREEN)
                dialog(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            idle()
            scenario.onActivity { activity ->
                assertEquals(KeyEvent.KEYCODE_PROG_RED, keys.keyFor(TvRemoteAction.SEARCH))
                openCapture(activity, TvRemoteAction.SEARCH)
                dialog(activity).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            }
            idle()
            assertNull(keys.keyFor(TvRemoteAction.SEARCH))
        }
    }

    @Test
    fun canceledKeyDoesNotBecomeAnAssignment() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openSettings(activity)
                openCapture(activity, TvRemoteAction.PLAY_PAUSE)
                val dialog = dialog(activity)
                dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED))
                dialog.dispatchKeyEvent(
                    KeyEvent.changeFlags(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED), KeyEvent.FLAG_CANCELED)
                )
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                assertNull(keys.keyFor(TvRemoteAction.PLAY_PAUSE))
            }
        }
    }

    @Test
    fun navigationIntentAndAssignedButtonsOpenTheExpectedMainActivityScreens() {
        // The first MainActivity launch asks for notifications; that system dialog owns focus.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        keys.assign(TvRemoteAction.HISTORY, KeyEvent.KEYCODE_PROG_RED)
        keys.assign(TvRemoteAction.SUBSCRIPTIONS, KeyEvent.KEYCODE_PROG_GREEN)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION, TvRemoteAction.BOOKMARKS.id)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitFocus(scenario)
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is BookmarkFragment)
                assertFalse(activity.intent.hasExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED)))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED)))
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is StatisticsPlaylistFragment)
            }
            idle()
            scenario.onActivity { activity ->
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_GREEN)))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_GREEN)))
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is SubscriptionFragment)
            }
        }
    }

    @Test
    fun mappedLettersStillTypeIntoTheSettingsSearchBox() {
        keys.assign(TvRemoteAction.BOOKMARKS, KeyEvent.KEYCODE_A)
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val main = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as MainSettingsFragment
                val entry = main.findPreference<Preference>("settings_search")!!
                entry.onPreferenceClickListener!!.onPreferenceClick(entry)
                activity.supportFragmentManager.executePendingTransactions()
            }
            awaitFocus(scenario)
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.toolbar_search_edit_text)
                input.requestFocus()
                input.setText("")
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A))
                assertEquals("a", input.text.toString())
                assertFalse(activity.isFinishing)
            }
        }
    }

    private fun <A : androidx.appcompat.app.AppCompatActivity> awaitFocus(scenario: ActivityScenario<A>) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            if (focused) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Activity did not receive window focus")
    }

    private fun openSettings(activity: SettingsActivity) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.settings_fragment_holder, TvRemoteSettingsFragment()).commitNow()
    }

    private fun openCapture(activity: SettingsActivity, action: TvRemoteAction) {
        val screen = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as TvRemoteSettingsFragment
        val preference = screen.findPreference<Preference>(action.preferenceKey)!!
        preference.onPreferenceClickListener!!.onPreferenceClick(preference)
        activity.supportFragmentManager.executePendingTransactions()
    }

    private fun dialog(activity: SettingsActivity) = (activity.supportFragmentManager.findFragmentByTag(TvRemoteKeyDialog.TAG) as TvRemoteKeyDialog).requireDialog() as AlertDialog

    private fun press(dialog: AlertDialog, code: Int) {
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
