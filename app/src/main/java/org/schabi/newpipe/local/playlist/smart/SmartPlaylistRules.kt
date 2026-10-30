/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist.smart

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class SmartPlaylistRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val watch: Int = 0,
    val maxMinutes: Int = 0,
    val keyword: String = "",
    val channels: List<Long> = emptyList(),
    val learningOnly: Boolean = false
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("watch", watch)
        .put("minutes", maxMinutes).put("keyword", keyword).put("channels", JSONArray(channels)).put("learning", learningOnly)

    companion object {
        fun parse(value: JSONObject): SmartPlaylistRule {
            val channels = value.getJSONArray("channels")
            require(channels.length() <= 500)
            return SmartPlaylistRule(
                UUID.fromString(value.getString("id")).toString(),
                value.getString("name").trim(),
                value.getInt("watch"),
                value.getInt("minutes"),
                value.getString("keyword").trim(),
                (0 until channels.length()).map { channels.getLong(it) }.distinct(),
                value.getBoolean("learning")
            ).also {
                require(it.name.isNotEmpty() && it.name.length <= 100 && it.keyword.length <= 200)
                require(it.watch in 0..2 && it.maxMinutes in 0..1440)
            }
        }
    }
}

internal class SmartPlaylistRules(context: Context, profile: String) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = "smart_playlists_${UUID.fromString(profile)}"

    fun read(): List<SmartPlaylistRule> = runCatching {
        val raw = preferences.getString(key, "[]").orEmpty()
        require(raw.length <= 256_000)
        val array = JSONArray(raw)
        require(array.length() <= 50)
        (0 until array.length()).map { SmartPlaylistRule.parse(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(rule: SmartPlaylistRule) {
        val checked = SmartPlaylistRule.parse(rule.json())
        val rules = read().filterNot { it.id == checked.id } + checked
        require(rules.size <= 50)
        write(rules)
    }

    fun delete(id: String) = write(read().filterNot { it.id == id })
    private fun write(rules: List<SmartPlaylistRule>) {
        val json = JSONArray().also { array -> rules.forEach { array.put(it.json()) } }.toString()
        require(json.length <= 256_000)
        preferences.edit().putString(key, json).apply()
    }
}
