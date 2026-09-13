package org.schabi.newpipe.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.schabi.newpipe.R
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.StreamTypeUtil
import us.shandian.giga.service.DownloadManagerService

class AutomaticDownloadWorker(context: Context, parameters: WorkerParameters) : RxWorker(context, parameters) {
    private val feed = FeedLoadManager(context)

    override fun createWork(): Single<Result> = Single.fromCallable {
        val context = applicationContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(AutomaticDownloads.ENABLED, false)) return@fromCallable Result.success()
        val subscriptions = SubscriptionManager(context).subscriptionTable().getAllDirect().associateBy { it.uid }
        val rules = AutomaticDownloads.rules(context).filter { rule ->
            subscriptions[rule.uid]?.let { it.url == rule.url && it.serviceId == rule.serviceId } == true
        }
        if (rules.isEmpty()) return@fromCallable Result.success()
        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.ic_wizestream_triangle_white).setContentTitle(context.getString(R.string.automatic_downloads))
            .setOngoing(true).setProgress(0, 0, true).build()
        setForegroundAsync(ForegroundInfo(0xAD01, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)).get()
        val result = feed.startLoading(ignoreOutdatedThreshold = true, onlySubscriptions = rules.map { it.uid }.toSet()).blockingGet()
        val connected = CountDownLatch(1)
        val binder = AtomicReference<DownloadManagerService.DownloadManagerBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder.set(service as DownloadManagerService.DownloadManagerBinder)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                binder.set(null)
            }
        }
        if (!context.bindService(Intent(context, DownloadManagerService::class.java), connection, Context.BIND_AUTO_CREATE)) throw IOException("Download service unavailable")
        try {
            if (!connected.await(15, TimeUnit.SECONDS)) throw IOException("Download service timed out")
            val service = binder.get() ?: throw IOException("Download service disconnected")
            if (service.askForSavePath()) throw IOException(context.getString(R.string.bulk_download_ask_path_error))
            var failed = result.any { it.isOnError }
            var totalQueued = 0
            AutomaticDownloadHistory(context).use { history ->
                result.mapNotNull { it.value }.forEach { update ->
                    val rule = rules.firstOrNull { it.uid == update.uid } ?: return@forEach
                    val initialized = history.contains(rule.uid, "")
                    val candidates = update.streams.distinctBy { it.url }.filter { stream ->
                        val live = StreamTypeUtil.isLiveStream(stream.streamType)
                        val selected = AutomaticDownloadPolicy.shouldDownload(stream.uploadDate?.date()?.timeInMillis, rule.since, initialized, history.contains(rule.uid, stream.url), live)
                        if (!initialized && !selected && !live) history.add(rule.uid, stream.url)
                        selected
                    }
                    history.add(rule.uid, "")
                    var queued = 0
                    for (stream in candidates) {
                        if (queued >= 3 || totalQueued >= 20 || isStopped || !preferences.getBoolean(AutomaticDownloads.ENABLED, false)) break
                        if (AutomaticDownloads.rules(context).none { it.uid == rule.uid }) break
                        val kind = if (rule.audio) 'a' else 'v'
                        if (service.downloadManager.hasMissionForSource(stream.url, kind)) {
                            history.add(rule.uid, stream.url)
                            continue
                        }
                        try {
                            val directory = if (rule.audio) service.mainStorageAudio else service.mainStorageVideo
                            if (directory == null || directory.isDirect == NewPipeSettings.useStorageAccessFramework(context) || directory.isInvalidSafStorage || !directory.canWrite()) throw IOException(context.getString(R.string.bulk_download_folder_error))
                            val info = ExtractorHelper.getStreamInfo(rule.serviceId, stream.url, false).blockingGet()
                            if (StreamTypeUtil.isLiveStream(info.streamType) || isStopped) continue
                            BulkDownloadMissionFactory.enqueue(context, directory, info, if (rule.audio) BulkDownloadMissionFactory.MediaType.AUDIO else BulkDownloadMissionFactory.MediaType.VIDEO, preferences.getInt(context.getString(R.string.default_download_threads), 3), 1, 1, false, preferences.getBoolean(AutomaticDownloads.WIFI_ONLY, true))
                            history.add(rule.uid, stream.url)
                            queued++
                            totalQueued++
                        } catch (exception: Exception) {
                            Log.w("AutomaticDownloads", "Unable to enqueue a new channel upload", exception)
                            failed = true
                        }
                    }
                }
            }
            preferences.edit().putString("automatic_download_status", context.getString(if (failed) R.string.automatic_download_partial_status else R.string.automatic_download_success_status, totalQueued)).apply()
            if (failed) Result.retry() else Result.success()
        } finally {
            context.unbindService(connection)
        }
    }.subscribeOn(Schedulers.io()).doOnDispose { feed.cancel() }.onErrorReturn { error ->
        Log.w("AutomaticDownloads", "Automatic download check failed", error)
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
            .putString("automatic_download_status", error.message ?: applicationContext.getString(R.string.general_error)).apply()
        Result.retry()
    }

    override fun onStopped() {
        feed.cancel()
        super.onStopped()
    }
}
