package org.schabi.newpipe.learning

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
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
import org.schabi.newpipe.player.TimestampChangeData
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/** Per-video notes are the same notes used by Learning, backup, and device synchronization. */
class VideoNotesDialog : DialogFragment() {
    private val disposables = CompositeDisposable()
    private var notes = emptyList<LearningNoteEntity>()
    private val rows = NotesAdapter()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
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
                    rows.notifyDataSetChanged()
                }, { error() })
        )
    }

    private fun play(note: LearningNoteEntity) {
        val appContext = requireContext().applicationContext
        disposables.add(
            io.reactivex.rxjava3.core.Maybe.fromCallable {
                NewPipeDatabase.getInstance(appContext).streamDAO().getStreamDirect(note.streamId)
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ stream ->
                    appContext.startService(
                        NavigationHelper.getPlayerTimestampIntent(
                            appContext,
                            TimestampChangeData(
                                stream.serviceId,
                                stream.url,
                                (note.timestampMillis / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                            )
                        )
                    )
                }, { error() })
        )
    }

    private inner class NotesAdapter : BaseAdapter() {
        override fun getCount(): Int = notes.size
        override fun getItem(position: Int): LearningNoteEntity = notes[position]
        override fun getItemId(position: Int): Long = notes[position].noteId.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_learning_note, parent, false)
            val note = getItem(position)
            row.findViewById<TextView>(R.id.learning_note_timestamp).apply {
                text = LearningNoteTime.format(note.timestampMillis)
                setOnClickListener { play(note) }
                setOnLongClickListener {
                    play(note)
                    true
                }
            }
            row.findViewById<TextView>(R.id.learning_note_text).apply {
                text = note.noteText
                setOnLongClickListener {
                    ShareUtils.copyToClipboard(context, note.noteText)
                    true
                }
            }
            row.setOnClickListener { actions(note) }
            row.setOnLongClickListener {
                ShareUtils.copyToClipboard(row.context, note.noteText)
                true
            }
            row.findViewById<View>(R.id.learning_note_edit).setOnClickListener { edit(note) }
            row.findViewById<View>(R.id.learning_note_delete).setOnClickListener { confirmDelete(note) }
            return row
        }
    }

    private fun confirmDelete(note: LearningNoteEntity) {
        MaterialAlertDialogBuilder(requireContext()).setMessage(note.noteText)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                disposables.add(
                    LearningNoteManager(requireContext()).delete(note.noteId)
                        .observeOn(AndroidSchedulers.mainThread()).subscribe({}, { error() })
                )
            }.show()
    }

    private fun actions(note: LearningNoteEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(arrayOf(getString(R.string.learning_note_edit), getString(R.string.delete))) { _, action ->
                if (action == 0) {
                    edit(note)
                } else {
                    confirmDelete(note)
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
