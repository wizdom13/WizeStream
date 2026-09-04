/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.schabi.newpipe.streams.io.StoredDirectoryHelper

internal class DownloadDestinationPolicyTest {
    @Test
    fun `Save As takes priority without reading configured storage`() {
        val storage = mock(StoredDirectoryHelper::class.java)

        val action = DownloadDestinationPolicy.resolve(
            askForSavePath = true,
            mainStorage = storage,
            useStorageAccessFramework = true,
            estimatedSize = 1_000
        )

        assertEquals(DownloadDestinationAction.SAVE_AS, action)
        verifyNoInteractions(storage)
    }

    @Test
    fun `missing configured storage requests a directory`() {
        val action = DownloadDestinationPolicy.resolve(
            askForSavePath = false,
            mainStorage = null,
            useStorageAccessFramework = false,
            estimatedSize = 1_000
        )

        assertEquals(DownloadDestinationAction.PICK_DIRECTORY, action)
    }

    @Test
    fun `direct storage is rejected when SAF is enabled`() {
        val storage = storage(isDirect = true)

        val action = DownloadDestinationPolicy.resolve(false, storage, true, 1_000)

        assertEquals(DownloadDestinationAction.PICK_DIRECTORY, action)
    }

    @Test
    fun `SAF storage is rejected when SAF is disabled`() {
        val storage = storage(isDirect = false)

        val action = DownloadDestinationPolicy.resolve(false, storage, false, 1_000)

        assertEquals(DownloadDestinationAction.PICK_DIRECTORY, action)
    }

    @Test
    fun `revoked SAF storage requests a new directory`() {
        val storage = storage(isDirect = false, isInvalidSafStorage = true)

        val action = DownloadDestinationPolicy.resolve(false, storage, true, 1_000)

        assertEquals(DownloadDestinationAction.PICK_DIRECTORY, action)
    }

    @Test
    fun `storage equal to estimated output size is insufficient`() {
        val storage = storage(isDirect = true, freeStorageSpace = 1_000)

        val action = DownloadDestinationPolicy.resolve(false, storage, false, 1_000)

        assertEquals(DownloadDestinationAction.INSUFFICIENT_STORAGE, action)
    }

    @Test
    fun `storage larger than estimated output uses configured directory`() {
        val storage = storage(isDirect = true, freeStorageSpace = 1_001)

        val action = DownloadDestinationPolicy.resolve(false, storage, false, 1_000)

        assertEquals(DownloadDestinationAction.USE_CONFIGURED_STORAGE, action)
    }

    private fun storage(
        isDirect: Boolean,
        isInvalidSafStorage: Boolean = false,
        freeStorageSpace: Long = Long.MAX_VALUE
    ): StoredDirectoryHelper {
        return mock(StoredDirectoryHelper::class.java).also {
            `when`(it.isDirect).thenReturn(isDirect)
            `when`(it.isInvalidSafStorage).thenReturn(isInvalidSafStorage)
            `when`(it.freeStorageSpace).thenReturn(freeStorageSpace)
        }
    }
}
