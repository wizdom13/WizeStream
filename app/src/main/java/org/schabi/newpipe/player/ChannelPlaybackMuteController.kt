/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.content.Context
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.ChannelPlaybackMutePreferences
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Resolves channel identity before applying sound, while preserving an explicit in-session choice. */
internal class ChannelPlaybackMuteController(
    context: Context,
    private val onChanged: () -> Unit
) {
    private val preferences = ChannelPlaybackMutePreferences(context)
    private var stream: Pair<Int, String>? = null
    private var channelKey: String? = null
    private var explicitChoice: Boolean? = null

    var isMuted: Boolean = false
        private set

    val hasChannelIdentity: Boolean get() = channelKey != null

    fun begin(item: PlayQueueItem?) {
        if (item == null) return
        select(item.serviceId, item.url, item.uploaderUrl.takeUnless { item.isLocalMedia })
    }

    fun select(serviceId: Int, url: String, uploaderUrl: String?) {
        val identity = serviceId to url
        if (stream != identity) {
            stream = identity
            explicitChoice = null
            channelKey = preferences.key(serviceId, uploaderUrl)
            isMuted = channelKey?.let(preferences::isMuted) ?: false
            onChanged()
        } else {
            resolveChannel(serviceId, uploaderUrl)
        }
    }

    fun onMetadata(info: StreamInfo) {
        // Extraction for the previous video may finish after the next one is selected.
        if (stream != (info.serviceId to info.url)) return
        resolveChannel(info.serviceId, info.uploaderUrl)
    }

    fun toggle() {
        isMuted = !isMuted
        explicitChoice = isMuted
        channelKey?.let { preferences.save(it, isMuted) }
        onChanged()
    }

    private fun resolveChannel(serviceId: Int, uploaderUrl: String?) {
        val key = preferences.key(serviceId, uploaderUrl) ?: return
        if (key == channelKey) return
        channelKey = key
        val explicit = explicitChoice
        if (explicit != null) {
            // A click made before the uploader was known belongs to this video, not stale metadata.
            preferences.save(key, explicit)
        } else {
            isMuted = preferences.isMuted(key)
        }
        onChanged()
    }
}
