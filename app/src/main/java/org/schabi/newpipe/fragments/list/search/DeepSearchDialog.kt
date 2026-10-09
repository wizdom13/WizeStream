package org.schabi.newpipe.fragments.list.search

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.function.BiConsumer
import org.schabi.newpipe.R

object DeepSearchDialog {
    @JvmStatic
    fun show(context: Context, onStart: BiConsumer<Int, Long>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        fun input(label: Int, value: String, decimal: Boolean): EditText {
            fields.addView(TextView(context).apply { setText(label) })
            return EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                setSingleLine()
                setText(value)
                contentDescription = context.getString(label)
                fields.addView(this)
            }
        }
        val pages = input(
            R.string.search_deep_custom_pages,
            preferences.getInt("search_deep_custom_pages", DeepSearchOptions.DEFAULT_PAGES).toString(),
            false
        )
        val delay = input(
            R.string.search_deep_delay_seconds,
            (preferences.getLong("search_deep_custom_delay", DeepSearchOptions.DEFAULT_DELAY_MILLIS) / 1000.0).toString(),
            true
        )
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.search_deep_custom)
            .setView(fields)
            .setPositiveButton(R.string.search_fetch_more, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pageCount = DeepSearchOptions.parsePages(pages.text.toString())
                val delayMillis = DeepSearchOptions.parseDelaySeconds(delay.text.toString())
                pages.error = if (pageCount == null) context.getString(R.string.search_deep_pages_invalid) else null
                delay.error = if (delayMillis == null) context.getString(R.string.search_deep_delay_invalid) else null
                if (pageCount == null || delayMillis == null) return@setOnClickListener
                preferences.edit()
                    .putInt("search_deep_custom_pages", pageCount)
                    .putLong("search_deep_custom_delay", delayMillis)
                    .apply()
                dialog.dismiss()
                onStart.accept(pageCount, delayMillis)
            }
        }
        dialog.show()
    }
}
