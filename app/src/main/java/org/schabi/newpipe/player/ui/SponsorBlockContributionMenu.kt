package org.schabi.newpipe.player.ui

import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.sponsorblock.ContributionDraft
import org.schabi.newpipe.sponsorblock.ContributionSegment
import org.schabi.newpipe.sponsorblock.SponsorBlockContributions

object SponsorBlockContributionMenu {
    @JvmStatic
    fun bind(button: View, player: Player) {
        val info = player.currentStreamInfo.orElse(null)
        val available = SponsorBlockContributions.eligible(button.context, info)
        button.visibility = if (available) View.VISIBLE else View.GONE
        button.setOnClickListener(if (available) View.OnClickListener { show(button, player) } else null)
    }

    private fun show(anchor: View, player: Player) {
        val info = player.currentStreamInfo.orElse(null) ?: return
        if (!SponsorBlockContributions.eligible(anchor.context, info)) return
        val dialog = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
            .setTitle(R.string.sb_contribute_title)
            .setItems(arrayOf(anchor.context.getString(R.string.sb_contribute_create), anchor.context.getString(R.string.sb_contribute_vote))) { _, which ->
                if (which == 0) create(anchor, player, info) else chooseVote(anchor, info)
            }.setNegativeButton(android.R.string.cancel, null).create()
        AnchoredPlaybackDialog.show(anchor, dialog)
    }

    private fun create(anchor: View, player: Player, info: StreamInfo) {
        val builder = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
        val context = builder.context
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun field(label: Int): TextInputEditText {
            val layout = TextInputLayout(context).apply { setHint(label) }
            val edit = TextInputEditText(context).apply { inputType = InputType.TYPE_CLASS_TEXT }
            layout.addView(edit)
            content.addView(layout)
            return edit
        }
        val start = field(R.string.sb_contribute_start)
        val end = field(R.string.sb_contribute_end)
        fun position(): Double = if (player.exoPlayerIsNull() || player.currentStreamInfo.orElse(null)?.url != info.url) 0.0 else player.exoPlayer.currentPosition.coerceAtLeast(0) / 1000.0
        start.setText(String.format(Locale.ROOT, "%.3f", position()))
        listOf(R.string.sb_contribute_start_here to start, R.string.sb_contribute_end_here to end).forEach { (label, edit) ->
            content.addView(
                MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    setText(label)
                    setOnClickListener { edit.setText(String.format(Locale.ROOT, "%.3f", position())) }
                }
            )
        }
        val labels = context.resources.getStringArray(R.array.sb_contribution_categories)
        val category = MaterialAutoCompleteTextView(context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, labels))
            setText(labels[0], false)
        }
        content.addView(
            TextInputLayout(context).apply {
                setHint(R.string.sb_contribute_category)
                addView(category)
            }
        )
        var pending: Disposable? = null
        val dialog = builder.setTitle(R.string.sb_contribute_create).setView(ScrollView(context).apply { addView(content) })
            .setMessage(R.string.sb_contribute_review)
            .setPositiveButton(R.string.sb_contribute_submit, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (pending?.isDisposed == false || !SponsorBlockContributions.eligible(context, info)) return@setOnClickListener
                val chosen = labels.indexOf(category.text.toString())
                val startTime = parseTimestamp(start.text.toString())
                val endTime = if (chosen == 8) startTime else parseTimestamp(end.text.toString())
                val draft = if (chosen >= 0 && startTime != null && endTime != null) ContributionDraft(SponsorBlockContributions.CATEGORIES[chosen], startTime, endTime, info.duration.toDouble()) else null
                try {
                    requireNotNull(draft).validate()
                } catch (_: IllegalArgumentException) {
                    start.error = context.getString(R.string.sb_contribute_invalid_times)
                    return@setOnClickListener
                }
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                pending = SponsorBlockContributions.submit(SponsorBlockContributions.videoId(info), SponsorBlockContributions.identity(context), draft)
                    .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                        {
                            Toast.makeText(context, R.string.sb_contribute_submitted, Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        },
                        {
                            button.isEnabled = true
                            Toast.makeText(context, R.string.sb_contribute_unconfirmed, Toast.LENGTH_LONG).show()
                        }
                    )
            }
        }
        AnchoredPlaybackDialog.show(anchor, dialog) { pending?.dispose() }
    }

    private fun chooseVote(anchor: View, info: StreamInfo) {
        val builder = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
        val dialog = builder.setTitle(R.string.sb_contribute_vote).setMessage(R.string.sb_contribute_loading)
            .setNegativeButton(android.R.string.cancel, null).create()
        var pending: Disposable? = null
        AnchoredPlaybackDialog.show(anchor, dialog) { pending?.dispose() }
        if (!dialog.isShowing) return
        pending = SponsorBlockContributions.segments(SponsorBlockContributions.videoId(info)).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(
                { segments ->
                    dialog.dismiss()
                    if (segments.isEmpty()) {
                        Toast.makeText(anchor.context, R.string.sb_contribute_no_segments, Toast.LENGTH_LONG).show()
                    } else {
                        val labels = anchor.resources.getStringArray(R.array.sb_contribution_categories)
                        val names = segments.map { "${labels[SponsorBlockContributions.CATEGORIES.indexOf(it.category)]} · ${format(it.start)}–${format(it.end)}" }.toTypedArray()
                        val choices = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor)).setTitle(R.string.sb_contribute_vote)
                            .setItems(names) { _, index -> vote(anchor, info, segments[index]) }.setNegativeButton(android.R.string.cancel, null).create()
                        AnchoredPlaybackDialog.show(anchor, choices)
                    }
                },
                {
                    dialog.dismiss()
                    Toast.makeText(anchor.context, R.string.sb_contribute_load_failed, Toast.LENGTH_LONG).show()
                }
            )
    }

    private fun vote(anchor: View, info: StreamInfo, segment: ContributionSegment) {
        val builder = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
        val options = arrayOf(anchor.context.getString(R.string.sb_contribute_upvote), anchor.context.getString(R.string.sb_contribute_downvote), anchor.context.getString(R.string.sb_contribute_undo))
        var selected = 0
        var pending: Disposable? = null
        val dialog = builder.setTitle(R.string.sb_contribute_vote).setSingleChoiceItems(options, 0) { _, index -> selected = index }
            .setPositiveButton(R.string.sb_contribute_send_vote, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                if (!button.isEnabled || !SponsorBlockContributions.eligible(anchor.context, info)) return@setOnClickListener
                button.isEnabled = false
                pending = SponsorBlockContributions.vote(segment.uuid, SponsorBlockContributions.identity(anchor.context), listOf(1, 0, 20)[selected])
                    .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                        {
                            Toast.makeText(anchor.context, R.string.sb_contribute_vote_sent, Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        },
                        {
                            button.isEnabled = true
                            Toast.makeText(anchor.context, R.string.sb_contribute_unconfirmed, Toast.LENGTH_LONG).show()
                        }
                    )
            }
        }
        AnchoredPlaybackDialog.show(anchor, dialog) { pending?.dispose() }
    }

    internal fun parseTimestamp(value: String): Double? {
        val parts = value.trim().split(":")
        if (parts.size !in 1..3) return null
        val numbers = parts.map { it.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n >= 0 } ?: return null }
        if (numbers.dropLast(1).any { it % 1 != 0.0 } || numbers.drop(1).any { it >= 60 }) return null
        return numbers.fold(0.0) { total, part -> total * 60 + part }.takeIf { it.isFinite() }
    }

    private fun format(seconds: Double): String = String.format(Locale.ROOT, "%d:%06.3f", (seconds / 60).toInt(), seconds % 60)
}
