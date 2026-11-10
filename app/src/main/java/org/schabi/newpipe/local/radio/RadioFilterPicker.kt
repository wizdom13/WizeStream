package org.schabi.newpipe.local.radio

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R

internal enum class RadioFilter(@StringRes val title: Int) {
    COUNTRY(R.string.radio_country),
    LANGUAGE(R.string.radio_language),
    GENRE(R.string.radio_genre)
}

internal data class RadioFilterOption(val value: String, val label: String, val alias: String = "") {
    fun matches(text: String): Boolean = label.contains(text, ignoreCase = true) || value.contains(text, ignoreCase = true) || alias.contains(text, ignoreCase = true)
}

internal fun radioCountries(): List<RadioFilterOption> = Locale.getISOCountries().map { code ->
    val region = Locale.Builder().setRegion(code).build()
    RadioFilterOption(code, region.displayCountry, region.getDisplayCountry(Locale.ENGLISH))
}.sortedWith(compareBy(Collator.getInstance()) { it.label })

internal class RadioFilterPicker(private val inflater: LayoutInflater, private val scope: CoroutineScope) {
    private val options = mutableMapOf<RadioFilter, List<RadioFilterOption>>()

    fun label(filter: RadioFilter, value: String): String {
        if (value.isBlank()) return inflater.context.getString(R.string.radio_filter_any)
        val available = if (filter == RadioFilter.COUNTRY) {
            options.getOrPut(filter, ::radioCountries)
        } else {
            options[filter].orEmpty()
        }
        return available.firstOrNull { it.value.equals(value, ignoreCase = true) }?.label ?: value
    }

    fun show(filter: RadioFilter, selected: String, onSelect: (String) -> Unit) {
        val content = inflater.inflate(R.layout.dialog_radio_filter_picker, null)
        val context = content.context
        val search = content.findViewById<EditText>(R.id.radio_dialog_edit)
        content.findViewById<TextInputLayout>(R.id.radio_filter_search).hint = context.getString(R.string.search)
        val list = content.findViewById<ListView>(R.id.radio_filter_choices)
        val progress = content.findViewById<View>(R.id.radio_filter_loading)
        val status = content.findViewById<TextView>(R.id.radio_filter_status)
        val any = RadioFilterOption("", context.getString(R.string.radio_filter_any))
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice)
        list.adapter = adapter
        var available = options[filter].orEmpty()
        var visible = emptyList<RadioFilterOption>()

        fun render() {
            val query = search.text.toString().trim()
            visible = listOf(any) + available.filter { it.matches(query) }
            adapter.setNotifyOnChange(false)
            adapter.clear()
            adapter.addAll(visible.map(RadioFilterOption::label))
            adapter.notifyDataSetChanged()
            list.clearChoices()
            val checked = visible.indexOfFirst { it.value.equals(selected, ignoreCase = true) }
            if (checked >= 0) list.setItemChecked(checked, true)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(filter.title)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .show()
        list.setOnItemClickListener { _, _, position, _ ->
            onSelect(visible[position].value)
            dialog.dismiss()
        }
        search.doAfterTextChanged { render() }
        render()
        if (filter in options) return

        progress.isVisible = true
        val load = scope.launch {
            try {
                available = withContext(Dispatchers.IO) { RadioDirectory.filterOptions(filter) }
                options[filter] = available
                render()
            } catch (_: IOException) {
                status.isVisible = true
            } finally {
                progress.isVisible = false
            }
        }
        dialog.setOnDismissListener { load.cancel() }
    }
}
