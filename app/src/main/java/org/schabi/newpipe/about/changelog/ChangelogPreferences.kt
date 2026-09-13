package org.schabi.newpipe.about.changelog

import android.content.Context

internal class ChangelogPreferences(context: Context) {
    // Presentation state belongs to this installation, outside synchronized user settings.
    private val preferences = context.getSharedPreferences("changelog", Context.MODE_PRIVATE)

    val lastSeenCode: Int
        get() = preferences.getInt("last_seen_release", 0)

    fun acknowledge(code: Int) {
        preferences.edit().putInt("last_seen_release", maxOf(code, lastSeenCode)).apply()
    }
}
