package org.schabi.newpipe.fragments.list.search

import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.filter.FilterItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SearchFilterPopupTest {
    @Test
    fun combinedYoutubeDialogDistinguishesMusicVideosAndPlaylists() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        var applied = ""
        SearchFilterDialog.showAdvanced(context, ServiceList.YouTube, emptyArray(), intArrayOf(), false, "", "") { selected, _, _, _ -> applied = selected }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val chips = descendants(dialog.window!!.decorView).filterIsInstance<Chip>()
        for ((regular, music, label) in listOf(
            Triple("videos", "music_videos", context.getString(R.string.videos_string)),
            Triple("playlists", "music_playlists", context.getString(R.string.playlists))
        )) {
            val regularChip = chips.single { (it.tag as? FilterItem)?.name == regular }
            val musicChip = chips.single { (it.tag as? FilterItem)?.name == music }
            assertEquals(label, regularChip.text.toString())
            assertEquals(context.getString(R.string.search_filter_source_label, context.getString(R.string.youtube_music), label), musicChip.text.toString())
        }
        chips.single { (it.tag as? FilterItem)?.name == "music_playlists" }.isChecked = true
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertEquals("music_playlists", applied)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun dedicatedMusicDialogKeepsConciseLabelsAndRestoresMusicSelection() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        var applied = ""
        SearchFilterDialog.showAdvanced(context, ServiceList.YouTube, arrayOf("music_playlists"), intArrayOf(), true, "", "") { selected, _, _, _ -> applied = selected }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val chips = descendants(dialog.window!!.decorView).filterIsInstance<Chip>()
        assertFalse(chips.any { (it.tag as? FilterItem)?.name in listOf("videos", "playlists") })
        val playlists = chips.single { (it.tag as? FilterItem)?.name == "music_playlists" }
        val videos = chips.single { (it.tag as? FilterItem)?.name == "music_videos" }
        assertTrue(playlists.isChecked)
        assertEquals(context.getString(R.string.playlists), playlists.text.toString())
        assertEquals(context.getString(R.string.videos_string), videos.text.toString())
        videos.isChecked = true
        assertFalse(playlists.isChecked)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertEquals("music_videos", applied)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun selectingVideoChipsAppliesCurrentFilterVariant() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        var content = ""
        var filters = emptyList<Int>()
        SearchFilterDialog.showAdvanced(context, ServiceList.YouTube, emptyArray(), intArrayOf(), false, "", "") { selected, sort, _, _ ->
            content = selected
            filters = sort
        }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val views = descendants(dialog.window!!.decorView)
        val videos = views.filterIsInstance<Chip>().single { (it.tag as? FilterItem)?.name == "videos" }
        videos.isChecked = true
        val longVideo = descendants(dialog.window!!.decorView).filterIsInstance<Chip>().single { (it.tag as? FilterItem)?.name == "long_video" }
        longVideo.isChecked = true
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertEquals("videos", content)
        assertTrue(filters.contains((longVideo.tag as FilterItem).identifier))
        assertFalse(dialog.isShowing)
    }

    @Test
    fun invalidDatesKeepDialogOpenAndValidRelativeDatesApply() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        var applied = ""
        SearchFilterDialog.showAdvanced(context, ServiceList.YouTube, arrayOf("videos"), intArrayOf(), false, "", "") { _, _, after, _ -> applied = after }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val after = descendants(dialog.window!!.decorView).filterIsInstance<EditText>().single { it.hint == context.getString(R.string.search_after_date) }
        after.setText("invalid")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        after.setText("2 months")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertEquals("2 months", applied)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun durationRangeAndOrderApplyTogether() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        var applied = Triple(0, 0L, 0L)
        SearchFilterDialog.showExtended(context, ServiceList.YouTube, arrayOf("videos"), intArrayOf(), false, "", "", 0, 0, 0) { _, _, _, _, order, minimum, maximum ->
            applied = Triple(order, minimum, maximum)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val views = descendants(dialog.window!!.decorView)
        views.filterIsInstance<Chip>().single { it.text == context.getString(R.string.search_longest_first) }.isChecked = true
        views.filterIsInstance<EditText>().single { it.hint == context.getString(R.string.search_minimum_hours) }.setText("15")
        views.filterIsInstance<EditText>().single { it.hint == context.getString(R.string.search_maximum_hours) }.setText("50")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertEquals(Triple(1, 54_000L, 180_000L), applied)
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else {
        emptyList()
    }
}
