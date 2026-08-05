/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LearningDashboardSnapshotTest {
    @Test
    fun classifiesPlaylistsAndCalculatesOverallCompletion() {
        val snapshot = LearningDashboardSnapshot(
            playlists = listOf(
                playlist(1, 2, 4),
                playlist(2, 3, 3),
                playlist(3, 0, 0)
            ),
            continueLearning = emptyList(),
            recentlyAnnotated = emptyList()
        )

        assertEquals(listOf(1L), snapshot.activePlaylists.map { it.playlistId })
        assertEquals(listOf(2L), snapshot.completedPlaylists.map { it.playlistId })
        assertEquals(5, snapshot.completedStreams)
        assertEquals(7, snapshot.eligibleStreams)
        assertEquals(71, snapshot.overallPercentage)
        assertFalse(snapshot.isEmpty)
    }

    private fun playlist(id: Long, completed: Int, eligible: Int) = LearningPlaylistSummary(id, "Playlist $id", null, eligible, completed)
}
