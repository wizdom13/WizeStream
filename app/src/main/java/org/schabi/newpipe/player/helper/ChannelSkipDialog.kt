package org.schabi.newpipe.player.helper

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo

object ChannelSkipDialog {
    @JvmStatic
    fun show(context: Context, info: StreamInfo, onSaved: Runnable) {
        if (!ChannelSkipPreferences.isAvailable(info)) return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        layout.addView(TextView(context).apply { text = context.getString(R.string.channel_skip_summary) })
        fun input(label: Int, value: Int): EditText {
            layout.addView(TextView(context).apply { setText(label) })
            return EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
                layout.addView(this)
            }
        }
        val start = input(R.string.channel_skip_start, ChannelSkipPreferences.startSeconds(context, info))
        val end = input(R.string.channel_skip_end, ChannelSkipPreferences.endSeconds(context, info))
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.channel_skip_title, info.uploaderName))
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val startValue = start.text.toString().toIntOrNull()
                val endValue = end.text.toString().toIntOrNull()
                val error = context.getString(R.string.channel_skip_invalid)
                start.error = if (startValue == null || startValue !in 0..ChannelSkipPreferences.MAX_SECONDS) error else null
                end.error = if (endValue == null || endValue !in 0..ChannelSkipPreferences.MAX_SECONDS) error else null
                if (start.error == null && end.error == null) {
                    ChannelSkipPreferences.save(context, info, startValue!!, endValue!!)
                    onSaved.run()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
