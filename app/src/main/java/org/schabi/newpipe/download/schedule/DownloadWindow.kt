package org.schabi.newpipe.download.schedule

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object DownloadWindow {
    const val ENABLED = "download_window_enabled"
    const val START = "download_window_start"
    const val END = "download_window_end"
    const val UNMETERED = "download_window_unmetered"
    const val CHARGING = "download_window_charging"
    private const val WORK = "download-window"

    @JvmStatic
    fun allowed(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(ENABLED, false)) return true
        val now = LocalTime.now()
        if (!contains(now.hour * 60 + now.minute, prefs.getInt(START, 0), prefs.getInt(END, 360))) return false
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (prefs.getBoolean(UNMETERED, true) && connectivity.isActiveNetworkMetered) return false
        if (prefs.getBoolean(CHARGING, false)) {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status != BatteryManager.BATTERY_STATUS_CHARGING && status != BatteryManager.BATTERY_STATUS_FULL) return false
        }
        return true
    }

    internal fun contains(minute: Int, start: Int, end: Int): Boolean {
        if (minute !in 0..1439 || start !in 0..1439 || end !in 0..1439) return false
        return when {
            start == end -> true
            start < end -> minute >= start && minute < end
            else -> minute >= start || minute < end
        }
    }

    @JvmStatic
    fun schedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val work = WorkManager.getInstance(context)
        if (!prefs.getBoolean(ENABLED, false)) {
            work.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.getBoolean(UNMETERED, true)) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(prefs.getBoolean(CHARGING, false))
            .build()
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DownloadWindowWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        )
    }
}
