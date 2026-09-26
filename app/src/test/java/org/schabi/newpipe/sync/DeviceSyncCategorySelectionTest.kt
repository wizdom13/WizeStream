/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSyncCategorySelectionTest {
    @Test
    fun `disabled optional categories do not make synchronization fail`() {
        val peer = TrustedPeer(
            peerId = "peer",
            publicKey = "key",
            deviceName = "Device",
            addresses = emptyList(),
            pairedAtEpochMillis = 1L
        )
        val profile = ProfileSyncResult(
            peer = peer,
            sentChanges = 0,
            receivedChanges = 0,
            addedProfiles = 0,
            updatedProfiles = 0
        )
        val attempt = DeviceSyncAttempt(
            peer = peer,
            profileResult = profile,
            subscriptionSkipped = true,
            playlistSkipped = true,
            watchHistorySkipped = true,
            searchHistorySkipped = true,
            learningNotesSkipped = true,
            structuredPreferenceSkipped = StructuredPreferenceCategory.entries.toSet()
        )

        val summary = DeviceSyncSummary(listOf(attempt))

        assertEquals(1, summary.succeeded)
        assertEquals(0, summary.failed)
    }

    @Test
    fun `enabled category without a result still makes synchronization fail`() {
        val peer = TrustedPeer(
            peerId = "peer",
            publicKey = "key",
            deviceName = "Device",
            addresses = emptyList(),
            pairedAtEpochMillis = 1L
        )
        val profile = ProfileSyncResult(
            peer = peer,
            sentChanges = 0,
            receivedChanges = 0,
            addedProfiles = 0,
            updatedProfiles = 0
        )
        val enabledCategory = StructuredPreferenceCategory.SETTINGS
        val attempt = DeviceSyncAttempt(
            peer = peer,
            profileResult = profile,
            subscriptionSkipped = true,
            playlistSkipped = true,
            watchHistorySkipped = true,
            searchHistorySkipped = true,
            learningNotesSkipped = true,
            structuredPreferenceSkipped = StructuredPreferenceCategory.entries
                .filterNot { it == enabledCategory }
                .toSet()
        )

        val summary = DeviceSyncSummary(listOf(attempt))

        assertEquals(0, summary.succeeded)
        assertEquals(1, summary.failed)
    }
}
