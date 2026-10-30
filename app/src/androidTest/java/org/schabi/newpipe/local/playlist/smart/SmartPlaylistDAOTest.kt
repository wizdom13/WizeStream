/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist.smart

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class SmartPlaylistDAOTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val owner = "00000000-0000-0000-0000-000000000000"
    private val other = "00000000-0000-0000-0000-000000000001"

    @After fun close() = database.close()

    private fun add(title: String, profile: String = owner, duration: Long = 600): Long {
        val id = database.streamDAO().insert(StreamEntity(serviceId = 0, url = "https://example.com/$title", title = title, streamType = StreamType.VIDEO_STREAM, duration = duration, uploader = "Teacher"))
        val playlist = database.playlistDAO().insert(PlaylistEntity(name = title, isThumbnailPermanent = false, thumbnailStreamId = id, displayIndex = 0, profileId = profile))
        database.playlistStreamDAO().insert(PlaylistStreamEntity(playlist, id, 0))
        return id
    }

    private fun matches(watch: Int = 0, seconds: Long = 0, keyword: String = "") = database.smartPlaylistDAO()
        .observe(owner, watch, seconds, keyword, true, emptyList(), false).blockingFirst().map { it.stream.uid }

    @Test fun excludesAnotherProfilesLibraryAndPlaybackState() {
        val own = add("Own")
        add("Foreign", other)
        database.streamStateDAO().insert(StreamStateEntity(own, 600_000, other))
        assertEquals(listOf(own), matches(watch = 1))
    }

    @Test fun partiallyWatchedRuleChangesAfterCompletion() {
        val id = add("Lesson")
        database.streamStateDAO().insert(StreamStateEntity(id, 100_000, owner))
        assertEquals(listOf(id), matches(watch = 2))
        database.streamStateDAO().update(StreamStateEntity(id, 600_000, owner))
        assertEquals(emptyList<Long>(), matches(watch = 2))
    }

    @Test fun durationAndLiteralKeywordRulesCombine() {
        val short = add("100% tutorial", duration = 60)
        add("Long tutorial", duration = 3600)
        assertEquals(listOf(short), matches(seconds = 120, keyword = "%"))
        assertEquals(listOf(short), matches(seconds = 120, keyword = "TUTORIAL"))
    }

    @Test fun selectedUnknownChannelNeverBroadensToAllChannels() {
        add("Lesson")
        val result = database.smartPlaylistDAO().observe(owner, 0, 0, "", false, listOf(999), false).blockingFirst()
        assertEquals(0, result.size)
    }
}
