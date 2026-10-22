package org.schabi.newpipe.local

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

/** Shared selection and block-moving UI for bookmarks and playlist contents. */
class CollectionSelectionController(
    private val context: Context,
    private val adapter: LocalItemListAdapter,
    private val separators: ListSeparators,
    private val changed: Runnable
) {
    private var dialog: AlertDialog? = null

    fun show() {
        val snapshot = adapter.itemsList.toList()
        if (snapshot.isEmpty()) return
        val headings = separators.labels(snapshot)
        val selected = mutableSetOf<Int>()
        val labels = snapshot.map { item ->
            when (item) {
                is PlaylistLocalItem -> item.orderingName.orEmpty()
                is PlaylistStreamEntry -> item.streamEntity.title.orEmpty()
                else -> ""
            }
        }.toTypedArray()
        close()
        val picker = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.collection_selected, 0))
            .setMultiChoiceItems(labels, BooleanArray(labels.size)) { current, index, checked ->
                if (checked) selected.add(index) else selected.remove(index)
                (current as AlertDialog).setTitle(context.getString(R.string.collection_selected, selected.size))
                current.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = selected.isNotEmpty()
            }
            .setNeutralButton(R.string.collection_select_all, null)
            .setPositiveButton(R.string.subscription_sort_move, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog = picker
        picker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        picker.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val select = selected.size != snapshot.size
            selected.clear()
            snapshot.indices.forEach {
                picker.listView.setItemChecked(it, select)
                if (select) selected.add(it)
            }
            picker.setTitle(context.getString(R.string.collection_selected, selected.size))
            picker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = selected.isNotEmpty()
        }
        picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (selected.isNotEmpty()) showDestinations(snapshot, headings, selected)
        }
    }

    private fun showDestinations(snapshot: List<LocalItem>, headings: Map<Int, String>, selected: Set<Int>) {
        val choices = listOf(
            context.getString(R.string.collection_move_start) to 0,
            context.getString(R.string.collection_move_end) to (snapshot.size + headings.size)
        ) + headings.map { (row, label) -> label to row + 1 }
        close()
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.subscription_sort_move)
            .setItems(choices.map { it.first }.toTypedArray()) { _, index ->
                val current = adapter.itemsList
                if (current.size != snapshot.size || snapshot.indices.any { current[it] !== snapshot[it] } ||
                    separators.labels(snapshot) != headings
                ) {
                    Toast.makeText(context, R.string.collection_changed_retry, Toast.LENGTH_LONG).show()
                    return@setItems
                }
                val reordered = separators.moveSelected(snapshot, selected, choices[index].second)
                adapter.clearStreamItemList()
                adapter.addItems(reordered)
                changed.run()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
    }
}
