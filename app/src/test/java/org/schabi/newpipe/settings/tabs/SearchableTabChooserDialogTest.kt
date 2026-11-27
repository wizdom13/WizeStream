package org.schabi.newpipe.settings.tabs

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], application = Application::class, qualifiers = "w400dp-h800dp")
class SearchableTabChooserDialogTest {
    private lateinit var host: ActivityController<TestActivity>
    private lateinit var dialog: AlertDialog
    private lateinit var search: EditText
    private lateinit var list: ListView
    private val chosen = mutableListOf<Int>()
    private val items = arrayOf(
        AddTabDialog.ChooseTabListItem.header("Custom content"),
        AddTabDialog.ChooseTabListItem(1, "My subscriptions", "YouTube", 0),
        AddTabDialog.ChooseTabListItem(2, "Favorite playlists", "Local", 0),
        AddTabDialog.ChooseTabListItem.header("More content"),
        AddTabDialog.ChooseTabListItem(3, "Science", "BiliBili", 0)
    )

    @Before
    fun showChooser() {
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
        SearchableTabChooser.show(host.get(), items) { _, index -> chosen += index }
        dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val views = descendants(dialog.window!!.decorView).toList()
        search = views.filterIsInstance<EditText>().single()
        list = views.filterIsInstance<ListView>().single()
        settle()
    }

    @After
    fun closeChooser() {
        if (::dialog.isInitialized) dialog.dismiss()
        if (::host.isInitialized) host.pause().stop().destroy()
    }

    @Test
    fun unmatchedQueryLeavesAnEmptyListAndHandlesStaleSelectionCallbacks() {
        list.setSelection(4)
        settle()

        search.setText("x")
        settle()

        assertEquals(0, list.adapter.count)
        assertEquals(AdapterView.INVALID_POSITION, list.selectedItemPosition)
        for (position in listOf(-1, 0, 4)) {
            assertEquals(AdapterView.INVALID_ROW_ID, list.adapter.getItemId(position))
            assertFalse(list.adapter.isEnabled(position))
        }
        list.performItemClick(null, 0, AdapterView.INVALID_ROW_ID)
        assertTrue(chosen.isEmpty())
        assertTrue(dialog.isShowing)
    }

    @Test
    fun clearingAnUnmatchedQueryRestoresOriginalOrderAndDisabledHeaders() {
        search.setText("missing")
        settle()
        search.setText("")
        settle()

        assertEquals(items.size, list.adapter.count)
        items.indices.forEach { position ->
            assertSame(items[position], list.adapter.getItem(position))
            assertEquals(position.toLong(), list.adapter.getItemId(position))
            assertEquals(!items[position].header, list.adapter.isEnabled(position))
        }
        list.performItemClick(null, 0, list.adapter.getItemId(0))
        assertTrue(chosen.isEmpty())
        assertTrue(dialog.isShowing)
    }

    @Test
    fun aFilteredClickSelectsTheOriginalTabAndDismissesTheDialog() {
        search.setText(" BILIBILI ")
        settle()

        assertEquals(1, list.adapter.count)
        assertSame(items[4], list.adapter.getItem(0))
        val row = list.adapter.getView(0, null, list)
        assertEquals("Science", row.findViewById<TextView>(R.id.tabName).text.toString())
        list.performItemClick(row, 0, list.adapter.getItemId(0))

        assertEquals(listOf(4), chosen)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun aClickFromThePreviousResultListCannotChooseADifferentTab() {
        search.setText("BiliBili")
        settle()
        val previousId = list.adapter.getItemId(0)
        search.setText("YouTube")
        settle()

        assertEquals(1, list.adapter.count)
        list.performItemClick(null, 4, previousId)
        list.performItemClick(null, 0, previousId)
        assertTrue(chosen.isEmpty())
        assertTrue(dialog.isShowing)

        list.performItemClick(null, 0, list.adapter.getItemId(0))
        assertEquals(listOf(1), chosen)
        assertFalse(dialog.isShowing)
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        val decor = dialog.window!!.decorView
        val metrics = host.get().resources.displayMetrics
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST)
        )
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
        }
    }
}
