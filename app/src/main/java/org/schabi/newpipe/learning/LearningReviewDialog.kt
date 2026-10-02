package org.schabi.newpipe.learning

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper

class LearningReviewDialog : DialogFragment() {
    private var subscription: Disposable? = null
    private var streams = emptyList<StreamEntity>()
    private var visible = emptyList<StreamEntity>()
    private var query = ""
    private var hardOnly = false
    private lateinit var rows: ArrayAdapter<String>
    private val profile get() = requireArguments().getString("profile")!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        query = savedInstanceState?.getString("query").orEmpty()
        hardOnly = savedInstanceState?.getBoolean("hard") ?: requireArguments().getBoolean("hard")
        rows = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf<String>())
        val search = SearchView(requireContext()).apply {
            setIconifiedByDefault(false)
            queryHint = getString(R.string.learning_review_search)
            setQuery(query, false)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?) = false
                override fun onQueryTextChange(text: String?): Boolean {
                    query = text.orEmpty()
                    render()
                    return true
                }
            })
        }
        val list = ListView(requireContext()).apply {
            adapter = rows
            setOnItemClickListener { _, _, position, _ -> visible.getOrNull(position)?.let(::actions) }
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(search)
            addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.density * 320).toInt()))
        }
        return MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.learning_review_title)
            .setView(content).setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.learning_review_hard_only, null).create()
    }

    override fun onStart() {
        super.onStart()
        (requireDialog() as androidx.appcompat.app.AlertDialog).getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            hardOnly = !hardOnly
            render()
        }
        subscription = LearningDifficulty.observe(requireContext(), requireArguments().getString("source"), profile)
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({
                streams = it
                render()
            }, {
                Toast.makeText(context, R.string.learning_review_error, Toast.LENGTH_LONG).show()
            })
    }

    private fun render() {
        if (!::rows.isInitialized) return
        visible = streams.filter {
            (!hardOnly || LearningDifficulty.rating(requireContext(), profile, it.serviceId, it.url) == 3) && it.title.contains(query, ignoreCase = true)
        }
        rows.clear()
        rows.addAll(visible.map { "${LearningDifficulty.label(requireContext(), LearningDifficulty.rating(requireContext(), profile, it.serviceId, it.url))} — ${it.title}" })
        dialog?.setTitle(getString(R.string.learning_review_title) + " (${visible.size})")
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setText(if (hardOnly) R.string.learning_review_all else R.string.learning_review_hard_only)
    }

    private fun actions(stream: StreamEntity) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(stream.title)
            .setItems(arrayOf(getString(R.string.learning_review_watch), getString(R.string.learning_difficulty_title))) { _, which ->
                if (which == 1) {
                    LearningDifficulty.choose(requireContext(), profile, stream.serviceId, stream.url) {}
                } else {
                    if (stream.isLocalMedia) {
                        NavigationHelper.playOnMainPlayer(requireContext(), LocalMediaPlayQueue(listOf(stream.toPlayQueueItem()), 0), false)
                    } else {
                        NavigationHelper.openVideoDetailFragment(requireContext(), parentFragmentManager, stream.serviceId, stream.url, stream.title, null, false)
                    }
                    dismiss()
                }
            }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putBoolean("hard", hardOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        subscription?.dispose()
        super.onStop()
    }

    companion object {
        @JvmStatic
        fun show(manager: FragmentManager, source: String?, profile: String, hardOnly: Boolean) {
            LearningReviewDialog().apply { arguments = bundleOf("source" to source, "profile" to profile, "hard" to hardOnly) }.show(manager, "learning-review")
        }

        fun bindShortcut(button: MaterialButton, manager: FragmentManager, source: String?): Disposable {
            val context = button.context
            button.isVisible = false
            if (!LearningDifficulty.isEnabled(context)) return Disposable.empty()
            val profile = ProfileManager.getActiveProfileId(context)
            return LearningDifficulty.observe(context, source, profile).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe({ streams ->
                    val count = streams.count { LearningDifficulty.rating(context, profile, it.serviceId, it.url) == 3 }
                    button.isVisible = count > 0
                    button.text = context.getString(R.string.learning_review_count, count)
                    button.setOnClickListener { show(manager, source, profile, true) }
                }, { button.isVisible = false })
        }
    }
}
