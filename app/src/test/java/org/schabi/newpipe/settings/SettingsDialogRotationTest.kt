package org.schabi.newpipe.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.App
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h800dp")
class SettingsDialogRotationTest {
    private lateinit var appInstance: MockedStatic<App>

    @Before
    fun provideApplication() {
        val app = Mockito.mock(App::class.java)
        Mockito.`when`(app.packageManager).thenReturn(RuntimeEnvironment.getApplication().packageManager)
        appInstance = Mockito.mockStatic(App::class.java)
        // Java production callers use the @JvmStatic bridge rather than the companion getter.
        appInstance.`when`<App> {
            App::class.java.getMethod("getInstance").invoke(null)
        }.thenReturn(app)
    }

    @After
    fun releaseApplication() {
        appInstance.close()
    }

    @Test
    fun listDialogDoesNotOutliveItsOwnerAcrossTwoRotations() = rotateWithDialog("list")

    @Test
    fun textDialogDoesNotOutliveItsOwnerAcrossTwoRotations() = rotateWithDialog("text")

    @Test
    fun multipleChoiceDialogDoesNotOutliveItsOwnerAcrossTwoRotations() = rotateWithDialog("multiple")

    private fun rotateWithDialog(key: String) {
        var controller = Robolectric.buildActivity(SettingsActivity::class.java).setup().visible()
        try {
            var activity = controller.get()
            var manager = activity.supportFragmentManager
            manager.executePendingTransactions()
            val root = manager.findFragmentById(R.id.settings_fragment_holder) as MainSettingsFragment
            activity.onPreferenceStartFragment(
                root,
                Preference(activity).apply { fragment = TestPreferences::class.java.name }
            )
            manager.executePendingTransactions()
            val preferences = manager.findFragmentById(R.id.settings_fragment_holder) as TestPreferences
            preferences.onDisplayPreferenceDialog(preferences.findPreference<Preference>(key)!!)
            manager.executePendingTransactions()
            assertNotNull(manager.findFragmentByTag(DIALOG_TAG))

            for (qualifiers in listOf("w1000dp-h400dp", "w400dp-h800dp")) {
                val state = Bundle()
                controller.pause().saveInstanceState(state).stop().destroy()
                RuntimeEnvironment.setQualifiers(qualifiers)
                controller = Robolectric.buildActivity(SettingsActivity::class.java)
                    .create(state).start().resume().visible()
                activity = controller.get()
                manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                assertNull(manager.findFragmentByTag(DIALOG_TAG))
                val restored = manager.findFragmentById(R.id.settings_fragment_holder) as TestPreferences
                assertEquals("original", restored.findPreference<EditTextPreference>("text")!!.text)
            }
            val restored = manager.findFragmentById(R.id.settings_fragment_holder) as TestPreferences
            restored.onDisplayPreferenceDialog(restored.findPreference<Preference>(key)!!)
            manager.executePendingTransactions()
            assertNotNull(manager.findFragmentByTag(DIALOG_TAG))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    class TestPreferences : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                title = "Test preferences"
                addPreference(
                    ListPreference(context).apply {
                        key = "list"
                        entries = arrayOf("One", "Two")
                        entryValues = arrayOf("one", "two")
                    }
                )
                addPreference(
                    EditTextPreference(context).apply {
                        key = "text"
                        setDefaultValue("original")
                    }
                )
                addPreference(
                    MultiSelectListPreference(context).apply {
                        key = "multiple"
                        entries = arrayOf("One", "Two")
                        entryValues = arrayOf("one", "two")
                    }
                )
            }
        }
    }

    companion object {
        private const val DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
