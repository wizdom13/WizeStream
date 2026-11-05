package org.schabi.newpipe.local.feed

import android.content.Context
import android.util.Log
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity

/** A local, profile-scoped picker; selecting a group does not start a refresh. */
internal class FeedGroupSelector(
    private val context: Context,
    private val button: MaterialButton,
    private val selectedId: () -> Long,
    private val onSelected: (Long, String) -> Unit
) {
    private var groups = emptyList<FeedGroupEntity>()
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private val subscription: Disposable

    init {
        button.setOnClickListener { showPicker() }
        subscription = FeedDatabaseManager(context).groups()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ current ->
                groups = current
                // Avoid accepting a stale item if a group is renamed/deleted by sync.
                dialog?.dismiss()
                val selected = groups.firstOrNull { it.uid == selectedId() }
                if (selectedId() != FeedGroupEntity.GROUP_ALL_ID) {
                    onSelected(selected?.uid ?: FeedGroupEntity.GROUP_ALL_ID, selected?.name.orEmpty())
                }
                button.isVisible = groups.isNotEmpty()
                updateLabel()
            }, { error -> Log.e("FeedGroupSelector", "Unable to load channel groups", error) })
    }

    private fun updateLabel() {
        val name = groups.firstOrNull { it.uid == selectedId() }?.name
            ?: context.getString(R.string.feed_group_filter_all)
        button.text = name
        button.contentDescription = context.getString(R.string.feed_group_filter_description, name)
    }

    private fun showPicker() {
        val snapshot = groups.toList()
        val labels = arrayOf(context.getString(R.string.feed_group_filter_all), *snapshot.map { it.name }.toTypedArray())
        val selectedIndex = snapshot.indexOfFirst { it.uid == selectedId() } + 1
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_groups_header_title)
            .setSingleChoiceItems(labels, selectedIndex) { picker, index ->
                picker.dismiss()
                val group = snapshot.getOrNull(index - 1)
                onSelected(group?.uid ?: FeedGroupEntity.GROUP_ALL_ID, group?.name.orEmpty())
                updateLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun dispose() {
        subscription.dispose()
        dialog?.dismiss()
        dialog = null
        button.setOnClickListener(null)
    }
}
