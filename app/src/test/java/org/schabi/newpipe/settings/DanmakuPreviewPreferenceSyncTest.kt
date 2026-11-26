package org.schabi.newpipe.settings

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w400dp-h800dp")
class DanmakuPreviewPreferenceSyncTest {
    private lateinit var host: ActivityController<TestActivity>
    private lateinit var fragment: VideoAudioSettingsFragment
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = listOf(
        Setting(R.string.danmaku_opacity_key, R.string.danmaku_opacity_title, 85, 61, 10),
        Setting(R.string.danmaku_text_size_key, R.string.danmaku_text_size_title, 18, 24, 12),
        Setting(R.string.danmaku_max_rows_key, R.string.danmaku_max_rows_title, 6, 9, 1),
        Setting(R.string.danmaku_duration_key, R.string.danmaku_duration_title, 8, 12, 4)
    )

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
        fragment = VideoAudioSettingsFragment()
        host.get().supportFragmentManager.beginTransaction().add(R.id.settings_fragment_holder, fragment).commitNow()
        settle()
    }

    @After
    fun tearDown() {
        ShadowDialog.getLatestDialog()?.dismiss()
        if (::host.isInitialized) host.pause().stop().destroy()
    }

    @Test
    fun applyingPreviewRefreshesTheExistingPreferencesAndTheirSliderRows() {
        val adapter = fragment.listView.adapter as PreferenceGroupAdapter
        val updates = mutableSetOf<Int>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                updates.addAll(positionStart until positionStart + itemCount)
            }
        })
        val original = settings.map(::control)
        val dialog = openPreview()
        changePreview(dialog)
        assertValues(settings.map { it.default })
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        settle()

        assertValues(settings.map { it.changed })
        for ((index, setting) in settings.withIndex()) {
            val preference = control(setting)
            assertSame(original[index], preference)
            assertEquals(setting.changed, preferences.getInt(key(setting), -1))
            val position = adapter.getPreferenceAdapterPosition(preference)
            assertTrue("Applying the preview must notify the visible preference adapter", position in updates)
            val holder = adapter.createViewHolder(fragment.listView, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            val slider = holder.findViewById(R.id.preference_material_slider) as Slider
            assertEquals(setting.changed.toFloat(), slider.value, 0f)
        }
    }

    @Test
    fun cancellingPreviewKeepsValuesAndRegularControlsStillPopulateANewPreview() {
        val dialog = openPreview()
        changePreview(dialog)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        settle()
        assertValues(settings.map { it.default })
        settings.forEach { assertEquals(it.default, preferences.getInt(key(it), -1)) }

        settings.forEach { control(it).value = it.changed }
        val reopened = openPreview()
        settings.forEach { assertEquals(it.changed - it.minimum, previewSlider(reopened, it).progress) }
        reopened.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
    }

    @Test
    fun detachedPreferencesStopObservingAndCatchUpWhenTheSameScreenReturns() {
        val controls = settings.map(::control)
        val manager = host.get().supportFragmentManager
        manager.beginTransaction().detach(fragment).commitNow()
        settle()
        val editor = preferences.edit()
        settings.forEach { editor.putInt(key(it), it.changed) }
        editor.commit()
        settle()
        assertEquals(settings.map { it.default }, controls.map { it.value })

        manager.beginTransaction().attach(fragment).commitNow()
        settle()
        settings.forEachIndexed { index, setting -> assertSame(controls[index], control(setting)) }
        assertValues(settings.map { it.changed })
    }

    @Test
    fun externalRefreshUsesTheDefaultAndBoundsWithoutWritingBack() {
        val opacity = settings.first()
        // Recreate with an existing key: AndroidX passes null rather than its XML default.
        preferences.edit().putInt(key(opacity), opacity.changed).commit()
        host.recreate()
        fragment = host.get().supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as VideoAudioSettingsFragment
        settle()
        preferences.edit().putInt(key(opacity), 500).commit()
        settle()
        assertEquals(100, control(opacity).value)
        assertEquals(500, preferences.getInt(key(opacity), -1))

        preferences.edit().remove(key(opacity)).commit()
        settle()
        assertEquals(opacity.default, control(opacity).value)
        assertFalse(preferences.contains(key(opacity)))
    }

    @Test
    @Config(sdk = [35])
    fun clearingSavedSettingsRestoresDefaultsWithoutRecreatingKeys() {
        settings.forEach { control(it).value = it.changed }
        preferences.edit().clear().commit()
        settle()
        assertValues(settings.map { it.default })
        settings.forEach { assertFalse(preferences.contains(key(it))) }
    }

    private fun assertValues(expected: List<Int>) = assertEquals(expected, settings.map { control(it).value })

    private fun key(setting: Setting): String = context.getString(setting.key)

    private fun control(setting: Setting): MaterialSliderPreference = requireNotNull(fragment.findPreference<MaterialSliderPreference>(key(setting)))

    private fun openPreview(): AlertDialog {
        val action = requireNotNull(fragment.findPreference<Preference>(context.getString(R.string.danmaku_preview_key)))
        assertTrue(requireNotNull(action.onPreferenceClickListener).onPreferenceClick(action))
        settle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun changePreview(dialog: AlertDialog) {
        for (setting in settings) {
            val slider = previewSlider(dialog, setting)
            val progress = setting.changed - setting.minimum
            slider.progress = progress
            requireNotNull(shadowOf(slider).onSeekBarChangeListener).onProgressChanged(slider, progress, true)
        }
    }

    private fun previewSlider(dialog: AlertDialog, setting: Setting): SeekBar = views(dialog.window!!.decorView)
        .filterIsInstance<SeekBar>().single { it.contentDescription == context.getString(setting.title) }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        host.get().supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(views(it)) }
    }

    private data class Setting(val key: Int, val title: Int, val default: Int, val changed: Int, val minimum: Int)

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = R.id.settings_fragment_holder })
        }
    }
}
