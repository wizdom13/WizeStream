/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

/** Presentation-only headings anchored to items, never inserted into playback or database order. */
class ListSeparators(context: Context, profileId: String, listId: String) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val preferenceKey = "list_separators_${profileId}_$listId"

    fun labels(items: List<LocalItem>): Map<Int, String> {
        val saved = read()
        return keys(items).mapIndexedNotNull { index, key ->
            saved.optString(key).takeIf { it.isNotBlank() }?.let { index to it }
        }.toMap()
    }

    fun edit(context: Context, items: List<LocalItem>, index: Int, changed: Runnable) {
        val key = keys(items).getOrNull(index) ?: return
        val previous = read().optString(key)
        val input = EditText(context).apply {
            setSingleLine()
            hint = context.getString(R.string.subscription_separator_name)
            setText(previous)
        }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.subscription_separator_name).setView(input)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null)
        if (previous.isNotBlank()) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                save(key, null)
                changed.run()
            }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                input.error = context.getString(R.string.subscription_separator_name)
            } else {
                save(key, name)
                changed.run()
                dialog.dismiss()
            }
        }
    }

    internal fun save(key: String, label: String?) {
        val data = read()
        if (label == null) data.remove(key) else data.put(key, label)
        preferences.edit().putString(preferenceKey, data.toString()).apply()
    }

    private fun read(): JSONObject = try {
        JSONObject(preferences.getString(preferenceKey, "{}") ?: "{}")
    } catch (_: org.json.JSONException) {
        JSONObject()
    }

    companion object {
        internal fun keys(items: List<LocalItem>): List<String> {
            val occurrences = mutableMapOf<String, Int>()
            return items.map { item ->
                val key = when (item) {
                    is PlaylistStreamEntry -> "stream:${item.streamId}"
                    is PlaylistRemoteEntity -> "remote:${item.serviceId}:${item.url}"
                    is PlaylistLocalItem -> "local:${item.uid}"
                    else -> "unsupported"
                }
                val occurrence = occurrences.getOrDefault(key, 0)
                occurrences[key] = occurrence + 1
                "$key:$occurrence"
            }
        }
    }
}
