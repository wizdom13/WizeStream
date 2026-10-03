package org.schabi.newpipe.settings

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class MaterialPreferenceDialogTest {
    @Test
    fun textDialogHonorsRejectedAcceptedAndCancelledChanges() {
        lateinit var preference: EditTextPreference
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as BasePreferenceFragment
                preference = EditTextPreference(activity).apply {
                    key = "material_dialog_test"
                    title = "Test"
                    isPersistent = false
                    text = "before"
                    setOnPreferenceChangeListener { _, value -> value == "accepted" }
                }
                fragment.preferenceScreen.addPreference(preference)
            }
            for ((input, button, expected) in listOf(
                Triple("rejected", AlertDialog.BUTTON_POSITIVE, "before"),
                Triple("accepted", AlertDialog.BUTTON_NEGATIVE, "before"),
                Triple("accepted", AlertDialog.BUTTON_POSITIVE, "accepted")
            )) {
                scenario.onActivity { activity ->
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as BasePreferenceFragment
                    fragment.onDisplayPreferenceDialog(preference)
                    activity.supportFragmentManager.executePendingTransactions()
                    val dialogFragment = activity.supportFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") as MaterialEditTextPreferenceDialog
                    val dialog = dialogFragment.requireDialog() as AlertDialog
                    dialog.findViewById<EditText>(android.R.id.edit)!!.setText(input)
                    dialog.getButton(button).performClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { assertEquals(expected, preference.text) }
            }
        }
    }
}
