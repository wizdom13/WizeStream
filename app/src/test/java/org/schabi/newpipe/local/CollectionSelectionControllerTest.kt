package org.schabi.newpipe.local

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.RecyclerView
import java.util.function.Consumer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CollectionSelectionControllerTest {
    class SelectionActivity : AppCompatActivity() {
        var selectionMode: ActionMode? = null
        override fun onSupportActionModeStarted(mode: ActionMode) {
            selectionMode = mode
        }
    }

    private lateinit var host: ActivityController<SelectionActivity>
    private lateinit var activity: SelectionActivity
    private lateinit var controller: CollectionSelectionController
    private val adapter = mock(LocalItemListAdapter::class.java)
    private val items = ArrayList<LocalItem>((0..2).map { mock(PlaylistStreamEntry::class.java) })
    private lateinit var toggle: Consumer<LocalItem>
    private var reordered = emptyList<LocalItem>()
    private var saves = 0

    @Before
    fun setup() {
        host = Robolectric.buildActivity(SelectionActivity::class.java)
        activity = host.get()
        activity.setTheme(R.style.LightTheme)
        host.setup()
        val recycler = RecyclerView(activity)
        activity.setContentView(recycler)
        `when`(adapter.itemsList).thenReturn(items)
        doAnswer {
            it.getArgument<Consumer<LocalItem>?>(0)?.let { listener -> toggle = listener }
            null
        }.`when`(adapter).setSelectionToggle(any())
        doAnswer {
            reordered = it.getArgument<List<LocalItem>>(0).toList()
            null
        }.`when`(adapter).addItems(any())
        controller = CollectionSelectionController(activity, recycler, adapter, ListSeparators(activity, "test", "selection")) { saves++ }
        controller.show()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun teardown() {
        controller.close()
        host.pause().stop().destroy()
    }

    @Test
    fun inlineSelectionMovesOnlyTheChosenDuplicateOccurrence() {
        val mode = activity.selectionMode!!
        assertNotNull(mode)
        val move = (0 until mode.menu.size()).map(mode.menu::getItem)
            .first { it.title == activity.getString(R.string.subscription_sort_move) }
        assertFalse(move.isEnabled)
        toggle.accept(items[1])
        controller.onPrepareActionMode(mode, mode.menu)
        assertTrue(move.isEnabled)
        controller.onActionItemClicked(mode, move)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.listView.performItemClick(null, 1, 1)
        assertEquals(listOf(items[0], items[2], items[1]), reordered)
        assertEquals(1, saves)
        verify(adapter).setSelectionToggle(null)
    }

    @Test
    fun changedCollectionCancelsSelectionWithoutSaving() {
        toggle.accept(items[1])
        val observer = ArgumentCaptor.forClass(RecyclerView.AdapterDataObserver::class.java)
        verify(adapter).registerAdapterDataObserver(observer.capture())
        items.removeAt(0)
        observer.value.onChanged()
        assertEquals(0, saves)
        verify(adapter).setSelectionToggle(null)
    }
}
