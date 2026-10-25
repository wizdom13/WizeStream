package org.schabi.newpipe.local

import android.graphics.Canvas
import android.graphics.Paint
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem

/** Select the visible thumbnail rows; the contextual toolbar remains available while scrolling. */
class CollectionSelectionController(
    private val activity: AppCompatActivity,
    private val recycler: RecyclerView,
    private val adapter: LocalItemListAdapter,
    private val separators: ListSeparators,
    private val changed: Runnable
) : ActionMode.Callback {
    private var dialog: AlertDialog? = null
    private var mode: ActionMode? = null
    private var snapshot = emptyList<LocalItem>()
    private var headings = emptyMap<Int, String>()
    private val selected = mutableSetOf<Int>()
    private var moving = false
    private val decoration = object : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val primary = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorPrimary)
            val surface = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorSurface)
            val onPrimary = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnPrimary)
            val density = parent.resources.displayMetrics.density
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                val position = adapter.getItemIndex(parent.getChildAdapterPosition(child))
                if (position !in snapshot.indices) continue
                val checked = position in selected
                child.isSelected = checked
                paint.style = Paint.Style.FILL
                if (checked) {
                    paint.color = ColorUtils.setAlphaComponent(primary, 45)
                    canvas.drawRect(child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat(), paint)
                }
                val right = child.right - 12 * density
                val top = child.top + 12 * density
                val size = 22 * density
                paint.color = if (checked) primary else surface
                canvas.drawRoundRect(right - size, top, right, top + size, 3 * density, 3 * density, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2 * density
                paint.color = if (checked) onPrimary else primary
                if (checked) {
                    canvas.drawLine(right - 17 * density, top + 11 * density, right - 10 * density, top + 17 * density, paint)
                    canvas.drawLine(right - 10 * density, top + 17 * density, right - 4 * density, top + 5 * density, paint)
                } else {
                    canvas.drawRoundRect(right - size, top, right, top + size, 3 * density, 3 * density, paint)
                }
            }
        }
    }
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = checkSnapshot()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = checkSnapshot()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = checkSnapshot()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = checkSnapshot()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = checkSnapshot()
    }

    fun show() {
        if (mode != null || adapter.itemsList.isEmpty()) return
        snapshot = adapter.itemsList.toList()
        headings = separators.labels(snapshot)
        mode = activity.startSupportActionMode(this) ?: return
        recycler.addItemDecoration(decoration)
        adapter.registerAdapterDataObserver(observer)
        adapter.setSelectionToggle { item ->
            val index = snapshot.indexOfFirst { it === item }
            if (index >= 0) {
                if (!selected.add(index)) selected.remove(index)
                refresh()
            }
        }
        refresh()
    }

    private fun refresh() {
        mode?.title = activity.getString(R.string.collection_selected, selected.size)
        mode?.invalidate()
        recycler.invalidateItemDecorations()
    }

    private fun isCurrent(): Boolean = adapter.itemsList.size == snapshot.size &&
        snapshot.indices.all { adapter.itemsList[it] === snapshot[it] } &&
        separators.labels(snapshot) == headings

    private fun checkSnapshot() {
        if (!moving && mode != null && !isCurrent()) {
            close()
            Toast.makeText(activity, R.string.collection_changed_retry, Toast.LENGTH_LONG).show()
        }
    }

    private fun showDestinations() {
        val choices = listOf(
            activity.getString(R.string.collection_move_start) to 0,
            activity.getString(R.string.collection_move_end) to (snapshot.size + headings.size)
        ) + headings.map { (row, label) -> label to row + 1 }
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.subscription_sort_move)
            .setItems(choices.map { it.first }.toTypedArray()) { _, index ->
                if (!isCurrent()) {
                    checkSnapshot()
                    return@setItems
                }
                moving = true
                val reordered = separators.moveSelected(snapshot, selected, choices[index].second)
                adapter.clearStreamItemList()
                adapter.addItems(reordered)
                changed.run()
                close()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(0, SELECT_ALL, 0, R.string.collection_select_all)
        menu.add(0, MOVE, 1, R.string.subscription_sort_move).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.findItem(MOVE).isEnabled = selected.isNotEmpty()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            SELECT_ALL -> {
                if (selected.size == snapshot.size) selected.clear() else selected.addAll(snapshot.indices)
                refresh()
            }

            MOVE -> if (selected.isNotEmpty()) showDestinations()

            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        this.mode = null
        dialog?.dismiss()
        dialog = null
        adapter.unregisterAdapterDataObserver(observer)
        recycler.removeItemDecoration(decoration)
        for (index in 0 until recycler.childCount) recycler.getChildAt(index).isSelected = false
        adapter.setSelectionToggle(null)
        selected.clear()
    }

    fun close() {
        mode?.finish()
    }

    private companion object {
        const val SELECT_ALL = 1
        const val MOVE = 2
    }
}
