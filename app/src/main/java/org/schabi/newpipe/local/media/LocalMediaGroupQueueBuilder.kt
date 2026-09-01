/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import kotlin.random.Random
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue

object LocalMediaGroupQueueBuilder {
    fun queue(
        group: LocalMediaGroup,
        shuffle: Boolean,
        random: Random = Random.Default
    ): LocalMediaPlayQueue = LocalMediaPlayQueue(
        items(group, shuffle, random).map(LocalMediaItem::toPlayQueueItem),
        0
    )

    internal fun items(
        group: LocalMediaGroup,
        shuffle: Boolean,
        random: Random = Random.Default
    ): List<LocalMediaItem> = if (shuffle) group.items.shuffled(random) else group.items
}
