/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import android.app.Application
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.ChannelPlaybackMuteController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChannelMuteSyncBoundaryTest {
    @Test
    fun existingChannelProfileSyncLeavesLocalMuteFlagsUntouchedAndDoesNotChangeItsProtocol() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        val repository = mock(StructuredPreferenceRecordRepository::class.java)
        `when`(repository.getRecordsByType(StructuredPreferenceCategory.CHANNEL_PROFILES, StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD))
            .thenReturn(emptyList())
        val adapter = ChannelProfileSyncAdapter(preferences, repository)
        val originalHash = adapter.snapshotHash()
        ChannelPlaybackMuteController(context) {}.apply {
            select(0, "video-a", "channel-a")
            toggle()
        }
        val key = preferences.all.keys.single()

        assertEquals(originalHash, adapter.snapshotHash())
        adapter.materialize()

        assertTrue(preferences.getBoolean(key, false))
        assertEquals(originalHash, adapter.snapshotHash())
    }
}
