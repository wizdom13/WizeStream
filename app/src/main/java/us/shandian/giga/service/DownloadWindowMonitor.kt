package us.shandian.giga.service

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import org.schabi.newpipe.download.schedule.DownloadWindow

/** Applies boundaries while the service is alive; WorkManager wakes it after longer idle periods. */
class DownloadWindowMonitor(private val service: DownloadManagerService, private val manager: DownloadManager) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = PreferenceManager.getDefaultSharedPreferences(service)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("download_window_") == true) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            evaluate()
            if (prefs.getBoolean(DownloadWindow.ENABLED, false)) handler.postDelayed(this, 30_000)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        if (prefs.getBoolean(DownloadWindow.ENABLED, false)) handler.post(tick)
    }

    fun evaluate() {
        if (!DownloadWindow.allowed(service)) {
            for (mission in manager.pendingMissionsSnapshot) {
                // pause() preserves enqueue intent; explicit user pause clears it.
                if (mission.running && !mission.isPsRunning) mission.pause()
            }
        } else {
            manager.runMissions()
        }
        service.updateForegroundState(manager.runningMissionsCount > 0)
    }

    fun close() {
        handler.removeCallbacks(tick)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
