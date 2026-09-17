/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import android.database.sqlite.SQLiteDatabaseLockedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMaintenanceManagerTest {
    @Test
    fun databaseLockDoesNotFailCompletedCleanup() {
        val completed = DatabaseMaintenanceManager.runBestEffortMaintenance {
            throw SQLiteDatabaseLockedException("database is locked")
        }

        assertFalse(completed)
    }

    @Test
    fun successfulMaintenanceIsReported() {
        assertTrue(
            DatabaseMaintenanceManager.runBestEffortMaintenance {
                // The optional maintenance completed without contention.
            }
        )
    }

    @Test
    fun unexpectedMaintenanceFailureIsNotHidden() {
        assertThrows(IllegalStateException::class.java) {
            DatabaseMaintenanceManager.runBestEffortMaintenance {
                throw IllegalStateException("unexpected")
            }
        }
    }
}
