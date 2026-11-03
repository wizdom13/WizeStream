package org.schabi.newpipe.local.cleanup

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.InfoCache

/** The same lock guards session changes and cleanup, so an old job cannot clear a new session. */
internal class ExitCleanupGate {
    private val activities = mutableSetOf<Activity>()
    private var playerOpen = false
    private var generation = 0L

    @Synchronized fun started(activity: Activity) {
        activities.add(activity)
        generation++
    }

    @Synchronized fun stopped(activity: Activity) {
        activities.remove(activity)
    }

    @Synchronized fun playerStarted() {
        playerOpen = true
        generation++
    }

    @Synchronized fun playerStopped() {
        playerOpen = false
    }

    @Synchronized fun ticket(): Long? = generation.takeIf { activities.isEmpty() && !playerOpen }

    @Synchronized fun run(ticket: Long, action: () -> Unit): Boolean {
        if (ticket != generation || activities.isNotEmpty() || playerOpen) return false
        action()
        return true
    }
}

object ExitCleanup {
    const val ENABLED = "exit_cleanup_enabled"
    const val COOKIES = "exit_cleanup_cookies"
    const val POSITIONS = "exit_cleanup_positions"
    internal const val WORK = "exit-cleanup"
    internal const val PROFILE = "profile"
    internal const val TOKEN = "token"
    private fun pending(context: Context) = context.getSharedPreferences("exit_cleanup_pending", Context.MODE_PRIVATE)
    private val gate = ExitCleanupGate()

    @Volatile private var initialized = false

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = foreground(activity)
            override fun onActivityStarted(activity: Activity) = foreground(activity)
            override fun onActivityStopped(activity: Activity) {
                gate.stopped(activity)
                schedule(application)
            }
            override fun onActivityDestroyed(activity: Activity) {
                gate.stopped(activity)
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
        initialized = true
    }

    private fun foreground(activity: Activity) {
        gate.started(activity)
        pending(activity).edit().clear().apply()
        WorkManager.getInstance(activity).cancelUniqueWork(WORK)
    }

    @JvmStatic fun isEnabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED, false)

    @JvmStatic fun playerStarted(context: Context) {
        gate.playerStarted()
        pending(context).edit().clear().apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    @JvmStatic fun playerStopped(context: Context) {
        gate.playerStopped()
        schedule(context)
    }

    private fun schedule(context: Context) {
        if (!initialized || !isEnabled(context) || gate.ticket() == null) return
        val token = UUID.randomUUID().toString()
        pending(context).edit().putString(TOKEN, token).apply()
        val request = OneTimeWorkRequestBuilder<ExitCleanupWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setInputData(workDataOf(PROFILE to ProfileManager.getActiveProfileId(context), TOKEN to token))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun clean(context: Context, profile: String, token: String): Boolean {
        if (!initialized) return false
        val ticket = gate.ticket() ?: return false
        return gate.run(ticket) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (pending(context).getString(TOKEN, null) != token || !prefs.getBoolean(ENABLED, false) ||
                ProfileManager.getActiveProfileId(context) != profile
            ) {
                return@run
            }
            // Do not use the broad manual cache wipe: it also deletes persistent feed rows.
            InfoCache.getInstance().clearCache()
            if (prefs.getBoolean(COOKIES, false)) {
                check(prefs.edit().putString(context.getString(R.string.recaptcha_cookies_key), "").commit())
                DownloaderImpl.getInstance().setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "")
            }
            if (prefs.getBoolean(POSITIONS, false)) {
                // Match the explicit manual action, including queue recovery and sync tombstones.
                HistoryRecordManager(context, profile).deleteCompleteStreamStateHistory().blockingGet()
            }
            pending(context).edit().remove(TOKEN).apply()
        }
    }
}

class ExitCleanupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val profile = inputData.getString(ExitCleanup.PROFILE) ?: return Result.success()
        val token = inputData.getString(ExitCleanup.TOKEN) ?: return Result.success()
        return try {
            if (isStopped) return Result.success()
            if (ExitCleanup.clean(applicationContext, profile, token)) Result.success() else Result.retry()
        } catch (error: Exception) {
            Log.w("ExitCleanup", "Cleanup deferred after an error", error)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
