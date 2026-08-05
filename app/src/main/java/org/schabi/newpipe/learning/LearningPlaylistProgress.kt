/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.model.StreamStateEntity

data class LearningProgress(val completed: Int, val eligible: Int) {
    val percentage: Int
        get() = if (eligible == 0) 0 else (completed * 100f / eligible).toInt()
}

object LearningPlaylistProgress {
    @JvmStatic
    fun calculate(streams: List<PlaylistStreamEntry>): LearningProgress {
        return calculateValues(streams.map { it.streamEntity.duration to it.progressMillis })
    }

    internal fun calculateValues(values: List<Pair<Long, Long>>): LearningProgress {
        var eligible = 0
        var completed = 0
        values.forEach { (duration, progress) ->
            if (duration <= 0) {
                return@forEach
            }
            eligible++
            if (StreamStateEntity(0, progress).isFinished(duration)) {
                completed++
            }
        }
        return LearningProgress(completed, eligible)
    }
}
