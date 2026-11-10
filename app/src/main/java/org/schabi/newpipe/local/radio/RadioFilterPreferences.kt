package org.schabi.newpipe.local.radio

import android.content.Context
import android.content.SharedPreferences

internal class RadioFilterPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("live_radio_filters", Context.MODE_PRIVATE)

    fun read(profileId: String): RadioFilters = RadioFilters(
        country = preferences.getString("$profileId.country", null).orEmpty(),
        language = preferences.getString("$profileId.language", null).orEmpty(),
        genre = preferences.getString("$profileId.genre", null).orEmpty()
    )

    fun save(profileId: String, filters: RadioFilters) {
        preferences.edit().putFilters(profileId, filters).apply()
    }

    fun saveSynced(changes: Map<String, RadioFilters>) {
        val editor = preferences.edit()
        changes.forEach { (profileId, filters) -> editor.putFilters(profileId, filters) }
        check(editor.commit()) { "Unable to save synchronized Live Radio filters" }
    }

    private fun SharedPreferences.Editor.putFilters(profileId: String, filters: RadioFilters) = apply {
        putString("$profileId.country", filters.country.ifEmpty { null })
        putString("$profileId.language", filters.language.ifEmpty { null })
        putString("$profileId.genre", filters.genre.ifEmpty { null })
    }
}
