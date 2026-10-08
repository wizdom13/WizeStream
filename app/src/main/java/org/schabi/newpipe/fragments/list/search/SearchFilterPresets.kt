package org.schabi.newpipe.fragments.list.search

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.schabi.newpipe.profiles.ProfileManager

/** Store names instead of extractor IDs, which can change when services add filters. */
class SearchFilterPresets(context: Context, serviceId: Int, musicOnly: Boolean) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = "search_filter_presets_${ProfileManager.getActiveProfileId(context)}_${serviceId}_$musicOnly"

    data class Preset @JvmOverloads constructor(val name: String, val content: String, val filters: List<String>, val after: String, val before: String, val order: Int = 0, val minimum: Long = 0, val maximum: Long = 0)

    fun read(): List<Preset> = try {
        val entries = JSONArray(preferences.getString(key, "[]"))
        (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val name = entry.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            val filters = entry.optJSONArray("filters") ?: JSONArray()
            Preset(name, entry.optString("content"), (0 until filters.length()).map { filters.getString(it) }, entry.optString("after"), entry.optString("before"), entry.optInt("order"), entry.optLong("minimum"), entry.optLong("maximum"))
        }
    } catch (_: JSONException) {
        emptyList()
    }

    fun save(preset: Preset) {
        require(preset.name.isNotBlank())
        write(read().filterNot { it.name == preset.name } + preset)
    }

    fun delete(name: String) = write(read().filterNot { it.name == name })

    private fun write(presets: List<Preset>) {
        val entries = JSONArray()
        presets.forEach { preset ->
            entries.put(JSONObject().put("name", preset.name).put("content", preset.content).put("filters", JSONArray(preset.filters)).put("after", preset.after).put("before", preset.before).put("order", preset.order).put("minimum", preset.minimum).put("maximum", preset.maximum))
        }
        preferences.edit().putString(key, entries.toString()).apply()
    }
}
