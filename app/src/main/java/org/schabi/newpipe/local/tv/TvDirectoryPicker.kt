package org.schabi.newpipe.local.tv

import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import org.schabi.newpipe.R

internal class TvDirectoryPicker(private val inflater: LayoutInflater, private val track: (AlertDialog) -> Unit) {
    private val context = inflater.context

    fun show(previous: TvDirectorySelection?, onSelect: (TvDirectorySelection) -> Unit) {
        var source = previous?.source ?: TvDirectorySource.IPTV_ORG
        val sources = TvDirectorySource.entries
        val dialog = MaterialAlertDialogBuilder(context).setTitle(R.string.tv_directory_source)
            .setSingleChoiceItems(sources.map { it.title }.toTypedArray(), sources.indexOf(source)) { _, index -> source = sources[index] }
            .setPositiveButton(R.string.tv_directory_continue) { _, _ -> countries(source, previous?.country?.code, onSelect) }
            .setNegativeButton(R.string.cancel, null).show()
        track(dialog)
    }

    private fun countries(source: TvDirectorySource, previous: String?, onSelect: (TvDirectorySelection) -> Unit) {
        val content = inflater.inflate(R.layout.dialog_radio_filter_picker, null)
        val input = content.findViewById<EditText>(R.id.radio_dialog_edit)
        content.findViewById<TextInputLayout>(R.id.radio_filter_search).setHint(R.string.search)
        content.findViewById<TextView>(R.id.radio_filter_status).apply {
            setText(R.string.tv_directory_notice)
            isVisible = true
        }
        val list = content.findViewById<ListView>(R.id.radio_filter_choices)
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice)
        val choices = TvDirectory.choices(context, source)
        val selected = previous ?: Locale.getDefault().country
        var visible = choices
        list.adapter = adapter
        fun render() {
            val text = input.text.toString().trim()
            visible = choices.filter { it.label().contains(text, true) || it.name.contains(text, true) || it.code.contains(text, true) }
            adapter.setNotifyOnChange(false)
            adapter.clear()
            adapter.addAll(visible.map { "${it.label()} (${it.code})" })
            adapter.notifyDataSetChanged()
            list.clearChoices()
            val position = visible.indexOfFirst { it.code == selected }
            if (position >= 0) list.setItemChecked(position, true)
        }
        val dialog = MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.tv_directory_country, source.title))
            .setView(content).setNegativeButton(R.string.cancel, null).show()
        track(dialog)
        input.doAfterTextChanged { render() }
        list.setOnItemClickListener { _, _, position, _ ->
            onSelect(TvDirectorySelection(source, visible[position]))
            dialog.dismiss()
        }
        render()
        val position = visible.indexOfFirst { it.code == selected }
        if (position >= 0) list.setSelection(position)
    }
}
