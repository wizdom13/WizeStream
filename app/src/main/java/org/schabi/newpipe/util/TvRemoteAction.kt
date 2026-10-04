/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import androidx.annotation.StringRes
import org.schabi.newpipe.R

/** Stable IDs are also preference keys; never persist enum ordinals. */
enum class TvRemoteAction(val id: String, @StringRes val title: Int, val playback: Boolean = false) {
    PLAY_PAUSE("play_pause", R.string.remote_play_pause, true),
    REWIND("rewind", R.string.rewind, true),
    FORWARD("forward", R.string.forward, true),
    PREVIOUS("previous", R.string.previous_stream, true),
    NEXT("next", R.string.next_stream, true),
    MUTE("mute", R.string.remote_mute, true),
    HOME("home", R.string.remote_home),
    SEARCH("search", R.string.search),
    FEED("feed", R.string.remote_feed),
    SUBSCRIPTIONS("subscriptions", R.string.tab_subscriptions),
    BOOKMARKS("bookmarks", R.string.bottom_navigation_tab_bookmarks),
    HISTORY("history", R.string.action_history),
    SETTINGS("settings", R.string.settings),
    SYNC("sync", R.string.device_sync_sync_now_title);

    val preferenceKey: String get() = "tv_remote_$id"

    companion object {
        @JvmStatic
        fun fromId(id: String?): TvRemoteAction? = entries.firstOrNull { it.id == id }
    }
}
