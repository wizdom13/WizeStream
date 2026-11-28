/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Intent
import androidx.core.content.IntentCompat
import org.schabi.newpipe.local.radio.isRadioHls
import org.schabi.newpipe.local.radio.secureRadioUrl
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** A direct stream's selected item must outlive the in-memory queue used to open its details. */
object DirectMediaNavigation {
    private const val ITEM_EXTRA = "org.schabi.newpipe.direct_media_item"

    @JvmStatic
    fun item(serviceId: Int, url: String?, queue: PlayQueue?): PlayQueueItem? = matchingItem(serviceId, url, queue?.item)

    private fun matchingItem(serviceId: Int, url: String?, item: PlayQueueItem?): PlayQueueItem? = item?.takeIf {
        it.serviceId == serviceId && (url == null || it.url == url) && it.url.isNotBlank() &&
            when (it.sourceType) {
                PlayQueueItem.SourceType.LOCAL -> serviceId == PlayQueueItem.LOCAL_SERVICE_ID
                PlayQueueItem.SourceType.RADIO -> serviceId == PlayQueueItem.RADIO_SERVICE_ID && secureRadioUrl(it.url) != null
                PlayQueueItem.SourceType.LIVE_TV -> serviceId == PlayQueueItem.LIVE_TV_SERVICE_ID
                else -> false
            }
    }

    @JvmStatic
    @JvmOverloads
    fun resolveQueue(
        serviceId: Int,
        url: String?,
        title: String,
        queue: PlayQueue?,
        savedItem: PlayQueueItem?,
        activeQueue: PlayQueue? = PlayerHolder.getInstance().player.orElse(null)?.playQueue
    ): PlayQueue? {
        if (serviceId >= 0) return queue?.takeUnless { it.item?.isDirectMedia == true }
        if (item(serviceId, url, queue) != null) return queue
        if (url.isNullOrBlank()) return null
        // A selected/saved descriptor takes precedence over a player that is still reconnecting.
        matchingItem(serviceId, url, savedItem)?.let { return LocalMediaPlayQueue(listOf(it), 0) }
        if (item(serviceId, url, activeQueue) != null) return activeQueue
        if (serviceId == PlayQueueItem.RADIO_SERVICE_ID) {
            // Older intents and saved states only contain a station URL. Never extract it as a
            // provider page, and only infer HLS from an actual manifest path.
            secureRadioUrl(url) ?: return null
            return LocalMediaPlayQueue(
                listOf(PlayQueueItem.radioStation(title, url, null, isRadioHls(url), null)),
                0
            )
        }
        return null
    }

    @JvmStatic
    fun saveToIntent(intent: Intent, queue: PlayQueue?) {
        val serviceId = intent.getIntExtra(KEY_SERVICE_ID, NO_SERVICE_ID)
        val url = intent.getStringExtra(KEY_URL)
        val resolved = resolveQueue(serviceId, url, intent.getStringExtra(KEY_TITLE).orEmpty(), queue, null)
        intent.removeExtra(ITEM_EXTRA)
        item(serviceId, url, resolved)?.let { intent.putExtra(ITEM_EXTRA, it) }
    }

    @JvmStatic
    fun restoreFromIntent(intent: Intent, cachedQueue: PlayQueue?): PlayQueue? {
        val serviceId = intent.getIntExtra(KEY_SERVICE_ID, NO_SERVICE_ID)
        val savedItem = if (serviceId < 0) IntentCompat.getSerializableExtra(intent, ITEM_EXTRA, PlayQueueItem::class.java) else null
        return resolveQueue(
            serviceId,
            intent.getStringExtra(KEY_URL),
            intent.getStringExtra(KEY_TITLE).orEmpty(),
            cachedQueue,
            savedItem
        )
    }
}
