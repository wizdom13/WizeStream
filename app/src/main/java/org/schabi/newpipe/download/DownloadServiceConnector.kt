/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder

internal data class DownloadServiceState(
    val mainStorageAudio: StoredDirectoryHelper?,
    val mainStorageVideo: StoredDirectoryHelper?,
    val downloadManager: DownloadManager,
    val askForSavePath: Boolean
)

internal fun interface DownloadServiceConnectedListener {
    fun onConnected(state: DownloadServiceState)
}

/** Starts the download service and owns its short-lived binding. */
internal class DownloadServiceConnector(
    context: Context,
    private val connectedListener: DownloadServiceConnectedListener
) {
    private val context = context.applicationContext ?: context
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val manager = service as DownloadManagerBinder
            connectedListener.onConnected(
                DownloadServiceState(
                    manager.mainStorageAudio,
                    manager.mainStorageVideo,
                    manager.downloadManager,
                    manager.askForSavePath()
                )
            )
            disconnect()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    fun connect() {
        if (bound) {
            return
        }
        val intent = Intent(context, DownloadManagerService::class.java)
        context.startService(intent)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        if (!bound) {
            return
        }
        context.unbindService(connection)
        bound = false
    }
}
