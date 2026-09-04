/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder

internal class DownloadServiceConnectorTest {
    @Test
    fun `connected service state is delivered and immediately unbound`() {
        val context = context(bindResult = true)
        val connection = ArgumentCaptor.forClass(ServiceConnection::class.java)
        val audioStorage = mock(StoredDirectoryHelper::class.java)
        val videoStorage = mock(StoredDirectoryHelper::class.java)
        val downloadManager = mock(DownloadManager::class.java)
        val binder = mock(DownloadManagerBinder::class.java)
        `when`(binder.mainStorageAudio).thenReturn(audioStorage)
        `when`(binder.mainStorageVideo).thenReturn(videoStorage)
        `when`(binder.downloadManager).thenReturn(downloadManager)
        `when`(binder.askForSavePath()).thenReturn(true)
        var received: DownloadServiceState? = null
        val connector = DownloadServiceConnector(context) { received = it }

        connector.connect()
        verify(context).bindService(
            any(Intent::class.java),
            connection.capture(),
            eq(Context.BIND_AUTO_CREATE)
        )
        connection.value.onServiceConnected(mock(ComponentName::class.java), binder)

        assertSame(audioStorage, received?.mainStorageAudio)
        assertSame(videoStorage, received?.mainStorageVideo)
        assertSame(downloadManager, received?.downloadManager)
        assertTrue(received?.askForSavePath == true)
        verify(context).unbindService(connection.value)

        connector.disconnect()
        verify(context, times(1)).unbindService(connection.value)
    }

    @Test
    fun `pending binding is disconnected once`() {
        val context = context(bindResult = true)
        val connection = ArgumentCaptor.forClass(ServiceConnection::class.java)
        val connector = DownloadServiceConnector(context) { }

        connector.connect()
        verify(context).bindService(
            any(Intent::class.java),
            connection.capture(),
            eq(Context.BIND_AUTO_CREATE)
        )
        connector.disconnect()
        connector.disconnect()

        verify(context, times(1)).unbindService(connection.value)
    }

    @Test
    fun `failed binding does not attempt to unbind`() {
        val context = context(bindResult = false)
        val connector = DownloadServiceConnector(context) { }

        connector.connect()
        connector.disconnect()

        verify(context, never()).unbindService(any(ServiceConnection::class.java))
    }

    private fun context(bindResult: Boolean): Context {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(
            context.bindService(
                any(Intent::class.java),
                any(ServiceConnection::class.java),
                eq(Context.BIND_AUTO_CREATE)
            )
        ).thenReturn(bindResult)
        return context
    }
}
