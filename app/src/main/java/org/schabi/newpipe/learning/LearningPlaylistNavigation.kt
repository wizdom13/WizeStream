/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

data class LearningPlaylistNavigation(
    val course: LearningPlaylistContext,
    val position: Int,
    val total: Int?,
    val next: PlayQueueItem?,
    val atEnd: Boolean,
    val loadError: Boolean
) {
    companion object {
        @JvmStatic
        fun from(queue: PlayQueue?, profileId: String, serviceId: Int, url: String?): LearningPlaylistNavigation? {
            val course = queue?.learningPlaylistContext ?: return null
            val item = queue.item ?: return null
            if (course.profileId != profileId || item.serviceId != serviceId || item.url != url || item.isAutoQueued || queue.isShuffled) return null
            val complete = queue.isComplete && !queue.hasLoadError()
            val next = queue.getItem(queue.index + 1)?.takeUnless { it.isAutoQueued }
            return LearningPlaylistNavigation(course, queue.index + 1, queue.streams.count { !it.isAutoQueued }.takeIf { complete }, next, complete && next == null, queue.hasLoadError())
        }
    }
}
