/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist.smart

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ContentBlockingHelper
import org.schabi.newpipe.util.NavigationHelper

class SmartPlaylistsDialog : DialogFragment() {
    private val work = CompositeDisposable()
    private val children = mutableListOf<AlertDialog>()
    private lateinit var rules: SmartPlaylistRules
    private lateinit var adapter: ArrayAdapter<String>
    private var saved = emptyList<SmartPlaylistRule>()
    private val profile get() = requireArguments().getString("profile")!!
    private fun valid() = isAdded && profile == ProfileManager.getActiveProfileId(requireContext()) && !KidsMode.requiresParentAccess(requireContext())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        rules = SmartPlaylistRules(requireContext(), profile)
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf<String>())
        refresh()
        return MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.smart_playlists_title)
            .setAdapter(adapter, null).setPositiveButton(R.string.smart_playlist_create, null)
            .setNegativeButton(R.string.close, null).create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (valid()) edit(null) }
        dialog.listView.setOnItemClickListener { _, _, index, _ -> saved.getOrNull(index)?.let { if (valid()) preview(it) } }
        dialog.listView.setOnItemLongClickListener { _, _, index, _ ->
            saved.getOrNull(index)?.let { if (valid()) edit(it) }
            true
        }
    }

    private fun refresh() {
        saved = rules.read()
        adapter.clear()
        adapter.addAll(saved.map { it.name })
    }

    private fun edit(existing: SmartPlaylistRule?) {
        if (existing == null && saved.size >= 50) {
            Toast.makeText(requireContext(), R.string.smart_playlist_limit, Toast.LENGTH_LONG).show()
            return
        }
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun field(hint: Int, value: String, max: Int) = TextInputEditText(context).apply {
            setHint(hint)
            setText(value)
            filters = arrayOf(InputFilter.LengthFilter(max))
            layout.addView(this)
        }
        val name = field(R.string.smart_playlist_name, existing?.name.orEmpty(), 100)
        val keyword = field(R.string.smart_playlist_keyword, existing?.keyword.orEmpty(), 200)
        val minutes = field(R.string.smart_playlist_duration, existing?.maxMinutes?.takeIf { it > 0 }?.toString().orEmpty(), 4).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val watch = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.smart_playlist_watch))
            setSelection(existing?.watch ?: 0)
            contentDescription = getString(R.string.smart_playlist_watch_label)
        }
        layout.addView(watch)
        val learning = MaterialCheckBox(context).apply {
            setText(R.string.smart_playlist_learning)
            isChecked = existing?.learningOnly == true
        }
        layout.addView(learning)
        var selected = existing?.channels.orEmpty()
        val channels = MaterialButton(context).apply {
            text = getString(R.string.smart_playlist_channels, selected.size)
            setOnClickListener {
                work.add(
                    Single.fromCallable { NewPipeDatabase.getInstance(context).subscriptionDAO().getAllDirectForProfile(profile) }
                        .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ subscriptions ->
                            if (!valid()) return@subscribe
                            val pending = selected.toMutableSet()
                            val chooser = MaterialAlertDialogBuilder(context).setTitle(R.string.smart_playlist_channels_title)
                                .setMultiChoiceItems(subscriptions.map { it.name ?: it.url.orEmpty() }.toTypedArray(), subscriptions.map { it.uid in selected }.toBooleanArray()) { _, index, checked ->
                                    if (checked) pending.add(subscriptions[index].uid) else pending.remove(subscriptions[index].uid)
                                }.setPositiveButton(R.string.ok) { _, _ ->
                                    selected = pending.toList()
                                    text = getString(R.string.smart_playlist_channels, selected.size)
                                }.setNegativeButton(R.string.cancel, null).create()
                            children.add(chooser)
                            chooser.show()
                        }, { error() })
                )
            }
        }
        layout.addView(channels)
        val editor = MaterialAlertDialogBuilder(context).setTitle(R.string.smart_playlist_rules)
            .setView(android.widget.ScrollView(context).apply { addView(layout) })
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null)
            .apply {
                if (existing != null) {
                    setNeutralButton(R.string.delete) { _, _ ->
                        if (valid()) {
                            rules.delete(existing.id)
                            refresh()
                        }
                    }
                }
            }.create()
        children.add(editor)
        editor.setOnShowListener {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!valid()) return@setOnClickListener
                val title = name.text?.toString()?.trim().orEmpty()
                val duration = minutes.text?.toString()?.takeIf(String::isNotBlank)?.toIntOrNull() ?: 0
                if (title.isEmpty() || duration !in 0..1440 || selected.size > 500) {
                    name.error = getString(R.string.smart_playlist_invalid)
                    return@setOnClickListener
                }
                runCatching {
                    rules.save(SmartPlaylistRule(existing?.id ?: java.util.UUID.randomUUID().toString(), title, watch.selectedItemPosition, duration, keyword.text?.toString()?.trim().orEmpty(), selected, learning.isChecked))
                }.onSuccess {
                    refresh()
                    editor.dismiss()
                }.onFailure { error() }
            }
        }
        editor.show()
    }

    private fun preview(rule: SmartPlaylistRule) {
        val context = requireContext()
        val rows = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, mutableListOf())
        var streams = emptyList<StreamWithState>()
        val preview = MaterialAlertDialogBuilder(context).setTitle(rule.name)
            .setAdapter(rows, null).setPositiveButton(R.string.smart_playlist_play, null)
            .setNeutralButton(R.string.smart_playlist_rules, null).setNegativeButton(R.string.close, null).create()
        children.add(preview)
        fun play(index: Int) {
            if (!valid() || index !in streams.indices) return
            val queue = LocalMediaPlayQueue(streams.map { it.stream.toPlayQueueItem() }, index, true)
            streams[index].stateProgressMillis?.let {
                if (!org.schabi.newpipe.database.stream.model.StreamStateEntity(streams[index].stream.uid, it, profile).isFinished(streams[index].stream.duration)) {
                    queue.setRecovery(index, it)
                }
            }
            NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
        }
        preview.setOnShowListener {
            preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            preview.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { play(0) }
            preview.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (valid()) {
                    preview.dismiss()
                    edit(rule)
                }
            }
            preview.listView.setOnItemClickListener { _, _, index, _ -> play(index) }
        }
        preview.show()
        val observer = NewPipeDatabase.getInstance(context).smartPlaylistDAO().observe(
            profile,
            rule.watch,
            rule.maxMinutes * 60L,
            rule.keyword,
            rule.channels.isEmpty(),
            rule.channels,
            rule.learningOnly
        ).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ found ->
            if (!valid()) {
                preview.dismiss()
                return@subscribe
            }
            val blocking = ContentBlockingHelper.getRules(context, ContentBlockingHelper.Target.REMOTE_PLAYLISTS)
            streams = found.filterNot { blocking.isBlocked(it.stream.toStreamInfoItem()) }
            rows.clear()
            rows.addAll(streams.map { "${it.stream.title}\n${it.stream.uploader}" })
            preview.setTitle(getString(R.string.smart_playlist_count, rule.name, streams.size))
            preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = streams.isNotEmpty()
        }, { error() })
        work.add(observer)
        preview.setOnDismissListener { observer.dispose() }
    }

    private fun error() {
        if (isAdded) Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        work.clear()
        children.forEach { it.dismiss() }
        children.clear()
        super.onDestroyView()
    }

    companion object {
        @JvmStatic fun show(manager: FragmentManager, context: Context) {
            if (manager.isStateSaved || manager.findFragmentByTag("smart-playlists") != null || KidsMode.requiresParentAccess(context)) return
            SmartPlaylistsDialog().apply { arguments = bundleOf("profile" to ProfileManager.getActiveProfileId(context)) }.show(manager, "smart-playlists")
        }
    }
}
