package org.schabi.newpipe.learning

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R

class LearningPlaylistNotesDialog : DialogFragment() {
    private val disposables = CompositeDisposable()
    private var notes = emptyList<LearningPlaylistNote>()
    private var exportNoteId: String? = null
    private var rows: ArrayAdapter<String>? = null
    private val textExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> export(uri, false) }
    private val csvExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> export(uri, true) }

    private fun sourceNotes() = NewPipeDatabase.getInstance(requireContext()).learningNoteDAO()
        .playlistNotes(requireArguments().getString("source")!!, requireArguments().getString("profile")!!)
        .subscribeOn(Schedulers.io())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        exportNoteId = savedInstanceState?.getString("exportNoteId")
        rows = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.learning_playlist_notes)
            .setAdapter(rows, null)
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.learning_notes_actions, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        (requireDialog() as androidx.appcompat.app.AlertDialog).listView.setOnItemClickListener { _, _, index, _ ->
            notes.getOrNull(index)?.let(::editActions)
        }
        (requireDialog() as androidx.appcompat.app.AlertDialog).getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            actions(null)
        }
        disposables.add(
            sourceNotes().observeOn(AndroidSchedulers.mainThread()).subscribe({ updated ->
                notes = updated
                rows?.clear()
                rows?.addAll(updated.map { "${it.title}\n${LearningNoteTime.format(it.note.timestampMillis)} — ${it.note.noteText.take(180)}" })
                requireDialog().setTitle(getString(R.string.learning_playlist_notes) + " (${updated.size})")
            }, { toast(R.string.learning_note_save_error) })
        )
    }

    private fun editActions(row: LearningPlaylistNote) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setItems(arrayOf(getString(R.string.learning_note_edit), getString(R.string.learning_notes_actions))) { _, which ->
                if (which == 0) {
                    LearningNoteDialog.show(requireContext(), row.note.timestampMillis, row.note) { timestamp, text ->
                        disposables.add(
                            LearningNoteManager(requireContext()).update(row.note, timestamp, text)
                                .observeOn(AndroidSchedulers.mainThread()).subscribe({}, { toast(R.string.learning_note_save_error) })
                        )
                    }
                } else {
                    actions(row.note.noteId)
                }
            }.show()
    }

    private fun actions(noteId: String?) {
        val labels = arrayOf(getString(R.string.learning_notes_export_text), getString(R.string.learning_notes_export_csv), getString(if (noteId == null) R.string.learning_notes_delete_all else R.string.delete))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.learning_notes_actions)
            .setItems(labels) { _, index ->
                exportNoteId = noteId
                when (index) {
                    0 -> textExport.launch("WizeStream-playlist-notes.txt")
                    1 -> csvExport.launch("WizeStream-playlist-notes.csv")
                    else -> confirmDelete(noteId)
                }
            }.show()
    }

    private fun confirmDelete(noteId: String?) {
        val ids = notes.filter { noteId == null || it.note.noteId == noteId }.map { it.note.noteId }
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.learning_notes_delete_all_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                disposables.add(
                    LearningNoteManager(requireContext()).deleteNotes(ids)
                        .observeOn(AndroidSchedulers.mainThread()).subscribe({}, { toast(R.string.learning_note_save_error) })
                )
            }.show()
    }

    private fun export(uri: Uri?, csv: Boolean) {
        if (uri == null) return
        val resolver = requireContext().contentResolver
        val selectedId = exportNoteId
        disposables.add(
            sourceNotes().firstOrError().flatMapCompletable { available ->
                Completable.fromAction {
                    val selected = available.filter { selectedId == null || it.note.noteId == selectedId }
                    val content = if (csv) LearningNotesExport.csv(selected) else LearningNotesExport.text(selected)
                    checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ toast(R.string.learning_notes_exported) }, { toast(R.string.learning_notes_export_error) })
        )
    }

    private fun toast(message: Int) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportNoteId", exportNoteId)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    override fun onDestroyView() {
        rows = null
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun show(manager: FragmentManager, sourceId: String, profileId: String) {
            LearningPlaylistNotesDialog().apply { arguments = bundleOf("source" to sourceId, "profile" to profileId) }
                .show(manager, "learning-playlist-notes")
        }
    }
}
