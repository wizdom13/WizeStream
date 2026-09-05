/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import java.util.concurrent.Future
import org.schabi.newpipe.local.media.LocalMediaMetadataLoader
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Owns cancellable local-media tag loading and rejects results for stale queue items. */
internal class PlayerLocalMetadataController(private val player: Player) {
    private var load: Future<*>? = null

    fun load(item: PlayQueueItem) {
        cancel()
        load = LocalMediaMetadataLoader.load(
            player.context,
            item
        ) metadataLoaded@{ metadata ->
            val currentMetadata = player.currentMetadata
            if (currentMetadata !is LocalMediaItemTag ||
                !currentMetadata.item.isSameItem(item)
            ) {
                return@metadataLoaded
            }
            if (!item.applyLocalMetadata(
                    metadata.title,
                    metadata.artist,
                    metadata.album,
                    metadata.durationSeconds
                )
            ) {
                return@metadataLoaded
            }
            player.playQueue?.notifyChange()
            player.notifyMetadataUpdateToListeners()
            player.notifyAudioTrackUpdateToListeners()
            player.UIs().call { ui -> ui.onMetadataChanged(player.currentMetadata) }
        }
    }

    fun cancel() {
        load?.cancel(true)
        load = null
    }
}
