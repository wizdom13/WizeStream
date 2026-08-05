/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.learning.model.LearningNoteEntity;
import org.schabi.newpipe.learning.LearningNoteDialog;
import org.schabi.newpipe.learning.LearningNoteManager;
import org.schabi.newpipe.learning.LearningNoteTime;
import org.schabi.newpipe.player.TimestampChangeData;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class LearningNotesFragment extends Fragment {
    private static final String ARG_SERVICE_ID = "service_id";
    private static final String ARG_STREAM_URL = "stream_url";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private int serviceId;
    private String streamUrl;
    private LearningNoteManager noteManager;
    private NotesAdapter adapter;
    private TextView emptyView;

    public static LearningNotesFragment getInstance(final int serviceId, final String streamUrl) {
        final var fragment = new LearningNotesFragment();
        final var arguments = new Bundle();
        arguments.putInt(ARG_SERVICE_ID, serviceId);
        arguments.putString(ARG_STREAM_URL, streamUrl);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle arguments = requireArguments();
        serviceId = arguments.getInt(ARG_SERVICE_ID);
        streamUrl = arguments.getString(ARG_STREAM_URL);
        noteManager = new LearningNoteManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_learning_notes, container, false);
        emptyView = root.findViewById(R.id.learning_notes_empty);
        final RecyclerView notes = root.findViewById(R.id.learning_notes_list);
        notes.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotesAdapter();
        notes.setAdapter(adapter);
        observeNotes();
        return root;
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        adapter = null;
        emptyView = null;
        super.onDestroyView();
    }

    private void observeNotes() {
        disposables.add(noteManager.observe(serviceId, streamUrl)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(notes -> {
                    if (adapter == null || emptyView == null) {
                        return;
                    }
                    adapter.setNotes(notes);
                    emptyView.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                }, error -> Toast.makeText(requireContext(),
                        R.string.learning_note_save_error, Toast.LENGTH_SHORT).show()));
    }

    private void seekTo(final LearningNoteEntity note) {
        requireContext().startService(NavigationHelper.getPlayerTimestampIntent(
                requireContext(),
                new TimestampChangeData(serviceId, streamUrl,
                        (int) Math.min(Integer.MAX_VALUE, note.getTimestampMillis() / 1_000))
        ));
    }

    private void edit(final LearningNoteEntity note) {
        LearningNoteDialog.show(requireContext(), note.getTimestampMillis(), note,
                (timestamp, text) -> disposables.add(noteManager.update(note, timestamp, text)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(updated -> Toast.makeText(
                                        requireContext(), R.string.learning_note_saved,
                                        Toast.LENGTH_SHORT).show(),
                                error -> Toast.makeText(requireContext(),
                                        R.string.learning_note_save_error,
                                        Toast.LENGTH_SHORT).show())));
    }

    private void confirmDelete(final LearningNoteEntity note) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.learning_note_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        disposables.add(noteManager.delete(note.getNoteId())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> Toast.makeText(
                                                requireContext(), R.string.learning_note_deleted,
                                                Toast.LENGTH_SHORT).show(),
                                        error -> Toast.makeText(requireContext(),
                                                R.string.learning_note_save_error,
                                                Toast.LENGTH_SHORT).show())))
                .show();
    }

    private final class NotesAdapter extends RecyclerView.Adapter<NoteViewHolder> {
        private final List<LearningNoteEntity> notes = new ArrayList<>();

        void setNotes(final List<LearningNoteEntity> newNotes) {
            notes.clear();
            notes.addAll(newNotes);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NoteViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                 final int viewType) {
            return new NoteViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_learning_note, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final NoteViewHolder holder, final int position) {
            final LearningNoteEntity note = notes.get(position);
            holder.timestamp.setText(LearningNoteTime.format(note.getTimestampMillis()));
            holder.text.setText(note.getNoteText());
            holder.itemView.setOnClickListener(view -> seekTo(note));
            holder.itemView.setOnLongClickListener(view -> {
                edit(note);
                return true;
            });
            holder.edit.setOnClickListener(view -> edit(note));
            holder.delete.setOnClickListener(view -> confirmDelete(note));
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }
    }

    private static final class NoteViewHolder extends RecyclerView.ViewHolder {
        private final TextView timestamp;
        private final TextView text;
        private final View edit;
        private final View delete;

        private NoteViewHolder(@NonNull final View itemView) {
            super(itemView);
            timestamp = itemView.findViewById(R.id.learning_note_timestamp);
            text = itemView.findViewById(R.id.learning_note_text);
            edit = itemView.findViewById(R.id.learning_note_edit);
            delete = itemView.findViewById(R.id.learning_note_delete);
        }
    }
}
