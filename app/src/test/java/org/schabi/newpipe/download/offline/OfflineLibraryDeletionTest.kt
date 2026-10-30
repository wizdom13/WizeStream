/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download.offline

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.service.DownloadManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineLibraryDeletionTest {
    private val storage = mock(StoredFileHelper::class.java)
    private val manager = mock(DownloadManager::class.java)
    private val mission = FinishedMission().apply { storage = this@OfflineLibraryDeletionTest.storage }

    @Test fun failedProviderDeletionKeepsTheDownloadRecord() {
        `when`(manager.finishedMissionsSnapshot).thenReturn(listOf(mission))
        `when`(storage.length()).thenReturn(100)
        `when`(storage.delete()).thenReturn(false)
        assertFalse(OfflineLibrary.deleteFile(manager, mission, 100))
        verify(manager, never()).deleteMission(mission, false)
    }

    @Test fun changedFileIsNeverDeleted() {
        `when`(manager.finishedMissionsSnapshot).thenReturn(listOf(mission))
        `when`(storage.length()).thenReturn(200)
        assertFalse(OfflineLibrary.deleteFile(manager, mission, 100))
        verify(storage, never()).delete()
    }

    @Test fun staleMissionIsNeverDeleted() {
        `when`(manager.finishedMissionsSnapshot).thenReturn(emptyList())
        assertFalse(OfflineLibrary.deleteFile(manager, mission, 100))
        verify(storage, never()).delete()
    }

    @Test fun successfulDeletionForgetsRecordWithoutDeletingTwice() {
        `when`(manager.finishedMissionsSnapshot).thenReturn(listOf(mission))
        `when`(storage.length()).thenReturn(100)
        `when`(storage.delete()).thenReturn(true)
        assertTrue(OfflineLibrary.deleteFile(manager, mission, 100))
        verify(manager).deleteMission(mission, false)
    }
}
