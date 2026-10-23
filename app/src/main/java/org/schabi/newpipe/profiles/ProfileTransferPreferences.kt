package org.schabi.newpipe.profiles

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.learning.LearningMode
import org.schabi.newpipe.local.bookmark.PlaylistCategories
import org.schabi.newpipe.local.subscription.SubscriptionLayout

internal object ProfileTransferPreferences {
    fun copy(context: Context, source: String, destination: String, replace: Boolean, playlists: Map<Long, Long>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sourceKey = PlaylistCategories.preferenceKey(source)
        val destinationKey = PlaylistCategories.preferenceKey(destination)
        val from = JSONObject(prefs.getString(sourceKey, null) ?: "{}")
        val to = JSONObject(if (replace) "{}" else prefs.getString(destinationKey, null) ?: "{}")
        val categories = to.optJSONArray("categories") ?: JSONArray()
        val categoryMap = mutableMapOf<String, String>()
        val incoming = from.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until incoming.length()) {
            val category = incoming.getJSONObject(i)
            val matching = (0 until categories.length()).map { categories.getJSONObject(it) }
                .firstOrNull { it.getString("name").equals(category.getString("name"), ignoreCase = true) }
            val sourceId = category.getString("id")
            val idTaken = (0 until categories.length()).any { categories.getJSONObject(it).getString("id") == sourceId }
            val resolvedId = matching?.getString("id") ?: if (idTaken) java.util.UUID.randomUUID().toString() else sourceId
            categoryMap[sourceId] = resolvedId
            if (matching == null) categories.put(JSONObject(category.toString()).put("id", resolvedId))
        }
        val memberships = to.optJSONObject("memberships") ?: JSONObject()
        val sourceMemberships = from.optJSONObject("memberships") ?: JSONObject()
        sourceMemberships.keys().forEach { key ->
            val mappedKey = if (key.startsWith("local:")) {
                playlists[key.removePrefix("local:").toLongOrNull()]?.let { "local:$it" }
            } else {
                key
            }
            if (mappedKey != null && !memberships.has(mappedKey)) {
                categoryMap[sourceMemberships.getString(key)]?.let { memberships.put(mappedKey, it) }
            }
        }
        val editor = prefs.edit().putString(destinationKey, to.put("categories", categories).put("memberships", memberships).toString())
        if (replace) {
            editor.putBoolean(LearningMode.profilePreferenceKey(destination), LearningMode.isProfileIncluded(context, source))
            // Separator positions are profile-local presentation; replacement copies their layout.
            prefs.all.filterKeys { it.startsWith("list_separators_${destination}_") }.keys.forEach(editor::remove)
        }
        prefs.all.filterKeys { it.startsWith("list_separators_${source}_") }.forEach { (key, value) ->
            var destinationLayoutKey = key.replace(source, destination)
            val playlistId = key.substringAfter("_playlist:", "").substringBefore("_").toLongOrNull()
            if (playlistId != null) {
                val newId = playlists[playlistId] ?: return@forEach
                destinationLayoutKey = destinationLayoutKey.replace("_playlist:$playlistId", "_playlist:$newId")
            }
            if (value is String && (replace || !prefs.contains(destinationLayoutKey))) {
                editor.putString(destinationLayoutKey, value)
            }
        }
        val sourceLayout = SubscriptionLayout(context, source).read()
        val destinationLayout = if (replace) emptyList() else SubscriptionLayout(context, destination).read()
        editor.putString("subscription_layout_$destination", SubscriptionLayout.encode((destinationLayout + sourceLayout).distinctBy { it.key }))
        check(editor.commit()) { "Could not save profile organization" }
    }
}
