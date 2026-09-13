package org.schabi.newpipe.download

import android.content.Context
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.player.helper.PlayerHelper

internal object DownloadSegmentDialog {
    fun show(context: Context, duration: Long, chapters: List<StreamSegment>, selected: String, onSelected: (String) -> Unit) {
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        content.addView(TextView(context).apply { setText(R.string.download_segments_description) })
        fun input(label: Int, value: String): TextInputEditText {
            val layout = TextInputLayout(context).apply { hint = context.getString(label) }
            val edit = TextInputEditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine()
                setText(value)
            }
            layout.addView(edit)
            content.addView(layout)
            return edit
        }
        val first = DownloadSegments.decode(selected).firstOrNull()
        val start = input(R.string.download_segment_start, PlayerHelper.getTimeString(first?.startMs ?: 0))
        val end = input(R.string.download_segment_end, PlayerHelper.getTimeString(first?.endMs ?: duration * 1000))
        val chapterButton = MaterialButton(context).apply {
            setText(R.string.download_select_chapters)
            isEnabled = chapters.isNotEmpty()
        }
        content.addView(chapterButton)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.download_segments)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.download_entire_stream) { _, _ -> onSelected("") }
            .setPositiveButton(R.string.ok, null)
            .create()
        chapterButton.setOnClickListener {
            val allChapters = chapters.filter { it.startTimeSeconds >= 0 && it.startTimeSeconds < duration }
                .distinctBy { it.startTimeSeconds }.sortedBy { it.startTimeSeconds }
            val sorted = allChapters.take(100)
            val checked = BooleanArray(sorted.size)
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.download_select_chapters)
                .setMultiChoiceItems(sorted.map { it.title }.toTypedArray(), checked) { _, index, value -> checked[index] = value }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val ranges = sorted.indices.filter { checked[it] }.map { index ->
                        DownloadSegment(sorted[index].startTimeSeconds * 1000L, (allChapters.getOrNull(index + 1)?.startTimeSeconds?.toLong() ?: duration) * 1000)
                    }
                    if (ranges.isNotEmpty()) {
                        onSelected(DownloadSegments.encode(DownloadSegments.normalize(ranges, duration * 1000)))
                        dialog.dismiss()
                    }
                }.show()
        }
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val range = DownloadSegment(DownloadSegments.parseTime(start.text.toString()), DownloadSegments.parseTime(end.text.toString()))
                    onSelected(DownloadSegments.encode(DownloadSegments.normalize(listOf(range), duration * 1000)))
                    dialog.dismiss()
                } catch (_: IllegalArgumentException) {
                    end.error = context.getString(R.string.download_segment_invalid)
                }
            }
        }
        dialog.show()
    }
}
