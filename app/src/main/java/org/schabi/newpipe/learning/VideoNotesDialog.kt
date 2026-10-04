package org.schabi.newpipe.learning

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.learning.model.LearningNoteEntity

/** Per-video notes are the same notes used by Learning, backup, and device synchronization. */
class VideoNotesDialog : DialogFragment() {
    private val disposables = CompositeDisposable()
    private var notes = emptyList<LearningNoteEntity>()
    private lateinit var rows: ArrayAdapter<String>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        rows = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(requireArguments().getString("title"))
            .setAdapter(rows) { _, index -> notes.getOrNull(index)?.let(::actions) }
            .setPositiveButton(R.string.learning_note_add, null)
            .setNeutralButton(R.string.playlist_video_notes_about, null)
            .setNegativeButton(R.string.close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { edit(null) }
        (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.playlist_video_notes_help)
                .setPositiveButton(R.string.ok, null).show()
        }
        // Replace the builder's listener so opening an editor does not dismiss the list.
        (requireDialog() as AlertDialog).listView.setOnItemClickListener { _, _, index, _ ->
            notes.getOrNull(index)?.let(::actions)
        }
        disposables.add(
            NewPipeDatabase.getInstance(requireContext()).learningNoteDAO()
                .getNotesForStream(requireArguments().getLong("streamId"))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updated ->
                    notes = updated
                    rows.clear()
                    rows.addAll(updated.map { "${LearningNoteTime.format(it.timestampMillis)} — ${it.noteText}" })
                }, { error() })
        )
    }

    private fun actions(note: LearningNoteEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(arrayOf(getString(R.string.learning_note_edit), getString(R.string.delete))) { _, action ->
                if (action == 0) {
                    edit(note)
                } else {
                    MaterialAlertDialogBuilder(requireContext()).setMessage(note.noteText)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            disposables.add(
                                LearningNoteManager(requireContext()).delete(note.noteId)
                                    .observeOn(AndroidSchedulers.mainThread()).subscribe({}, { error() })
                            )
                        }.show()
                }
            }.show()
    }

    private fun edit(note: LearningNoteEntity?) {
        LearningNoteDialog.show(requireContext(), note?.timestampMillis ?: 0, note) { time, text ->
            if (!isAdded) return@show
            val manager = LearningNoteManager(requireContext())
            val save = if (note == null) manager.createForStream(requireArguments().getLong("streamId"), time, text) else manager.update(note, time, text)
            disposables.add(save.observeOn(AndroidSchedulers.mainThread()).subscribe({}, { error() }))
        }
    }

    private fun error() {
        context?.let { Toast.makeText(it, R.string.learning_note_save_error, Toast.LENGTH_SHORT).show() }
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    companion object {
        @JvmStatic
        fun show(manager: FragmentManager, streamId: Long, title: String) {
            if (manager.isStateSaved || manager.findFragmentByTag("video_notes") != null) return
            VideoNotesDialog().apply { arguments = bundleOf("streamId" to streamId, "title" to title) }
                .show(manager, "video_notes")
        }
    }
}
