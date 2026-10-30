/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRetentionPolicyTest {
    private fun item(id: String, channel: String = "channel", date: Long = 0, bytes: Long = 100, watched: Boolean = false, keep: Boolean = false) = ManagedDownload(id, channel, date, bytes, watched, keep)

    @Test fun disabledRulesNeverDelete() {
        assertEquals(emptySet<String>(), OfflineRetentionPolicy.candidates(listOf(item("a", watched = true)), RetentionRules(deleteWatched = true)))
    }

    @Test fun latestLimitIsPerChannelAndProtectedOldFilesSurvive() {
        val items = listOf(item("a", date = 1, keep = true), item("b", date = 2), item("c", date = 3), item("d", channel = "other"))
        assertEquals(setOf("b"), OfflineRetentionPolicy.candidates(items, RetentionRules(true, latest = 1)))
    }

    @Test fun watchedRuleHonorsPermanentProtection() {
        val items = listOf(item("a", watched = true), item("b", watched = true, keep = true), item("c"))
        assertEquals(setOf("a"), OfflineRetentionPolicy.candidates(items, RetentionRules(true, deleteWatched = true)))
    }

    @Test fun storageBudgetEvictsOldestUnprotectedFiles() {
        val items = listOf(item("new", date = 3), item("old", date = 1), item("middle", date = 2))
        assertEquals(setOf("old", "middle"), OfflineRetentionPolicy.candidates(items, RetentionRules(true, budgetBytes = 100)))
    }

    @Test fun protectedFilesMayExceedBudget() {
        val items = listOf(item("protected", bytes = 1000, keep = true), item("other"))
        assertEquals(setOf("other"), OfflineRetentionPolicy.candidates(items, RetentionRules(true, budgetBytes = 1)))
    }

    @Test fun combinedRulesDoNotDoubleCountRemovedBytes() {
        val items = listOf(item("watched", date = 0, watched = true), item("old", date = 1), item("new", date = 2))
        assertEquals(setOf("watched"), OfflineRetentionPolicy.candidates(items, RetentionRules(true, latest = 2, budgetBytes = 200, deleteWatched = true)))
    }
}
