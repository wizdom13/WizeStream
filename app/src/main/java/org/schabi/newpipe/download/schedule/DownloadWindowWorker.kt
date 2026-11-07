package org.schabi.newpipe.download.schedule

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.R
import us.shandian.giga.service.DownloadManagerService

class DownloadWindowWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val context = applicationContext
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DownloadWindow.ENABLED, false) ||
            !DownloadWindow.allowed(context)
        ) {
            return Result.success()
        }
        val folders = listOfNotNull(context.getExternalFilesDir("pending_downloads"), File(context.filesDir, "pending_downloads"))
        if (folders.none { it.listFiles()?.isNotEmpty() == true }) return Result.success()
        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.ic_wizestream_triangle_white).setContentTitle(context.getString(R.string.download_window_title)).build()
        setForegroundAsync(ForegroundInfo(0xAD02, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)).get()
        val connected = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                // Keep the existing download service alive after the short worker unbinds.
                try {
                    if (!isStopped && DownloadWindow.allowed(context)) {
                        context.startService(Intent(context, DownloadManagerService::class.java))
                        (binder as DownloadManagerService.DownloadManagerBinder).resumeScheduledDownloads()
                    }
                } catch (_: RuntimeException) {
                    failed.set(true)
                } finally {
                    connected.countDown()
                }
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        if (!context.bindService(Intent(context, DownloadManagerService::class.java), connection, Context.BIND_AUTO_CREATE)) return Result.retry()
        return try {
            if (connected.await(15, TimeUnit.SECONDS) && !failed.get()) Result.success() else Result.retry()
        } finally {
            context.unbindService(connection)
        }
    }
}
