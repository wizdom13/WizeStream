package org.schabi.newpipe.learning

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.profiles.ProfileManager

object LearningReminders {
    const val EXTRA_PROFILE_ID = "org.schabi.newpipe.LEARNING_REMINDER_PROFILE"
    private const val CHANNEL = "learning_reminders"
    private const val WORK = "learning_daily_reminders"
    private const val PREFIX = "learning_reminder_"

    private fun preferences(context: Context) = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private fun key(profileId: String, suffix: String) = "$PREFIX${profileId}_$suffix"

    fun isEnabled(context: Context, profileId: String): Boolean = preferences(context).getBoolean(key(profileId, "enabled"), false)

    fun minuteOfDay(context: Context, profileId: String): Int = preferences(context).getInt(key(profileId, "minute"), 9 * 60).coerceIn(0, 1439)

    fun setEnabled(context: Context, profileId: String, enabled: Boolean) {
        preferences(context).edit().putBoolean(key(profileId, "enabled"), enabled).apply()
        initialize(context)
    }

    fun setMinuteOfDay(context: Context, profileId: String, minute: Int) {
        preferences(context).edit().putInt(key(profileId, "minute"), minute.coerceIn(0, 1439)).apply()
    }

    @JvmStatic
    fun initialize(context: Context) {
        val profiles = ProfileManager.getProfiles(context)
        val enabled = profiles.filter { LearningMode.isEnabled(context, it.id) && isEnabled(context, it.id) }
        profiles.filter { it !in enabled }.forEach { cancelNotification(context, it.id) }
        val manager = WorkManager.getInstance(context)
        if (enabled.isEmpty()) {
            manager.cancelUniqueWork(WORK)
        } else {
            // Check local wall time instead of adding 24 hours, so DST and time-zone changes
            // keep the requested time. WorkManager persists the schedule across reboots.
            manager.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LearningReminderWorker>(15, TimeUnit.MINUTES).build()
            )
        }
    }

    @JvmStatic
    fun intent(context: Context, profileId: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData("wizestream://learning-reminder/$profileId".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_PROFILE_ID, profileId)

    /** Validate old notifications before changing profiles, and never start playback. */
    @JvmStatic
    fun selectProfile(context: Context, intent: Intent?): Boolean {
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID) ?: return false
        if (ProfileManager.getProfile(context, profileId) == null || !LearningMode.isEnabled(context, profileId)) {
            intent.removeExtra(EXTRA_PROFILE_ID)
            return false
        }
        if (ProfileManager.getActiveProfileId(context) != profileId) {
            PlayerHolder.getInstance().player.ifPresent { it.pause() }
            ProfileManager.setActiveProfile(context, profileId)
        }
        return true
    }

    @Synchronized
    fun sendDueReminders(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val prefs = preferences(context)
        val dao = NewPipeDatabase.getInstance(context).learningContentDAO()
        ProfileManager.getProfiles(context).forEach { profile ->
            if (LearningMode.isEnabled(context, profile.id) && isEnabled(context, profile.id) &&
                LearningReminderPolicy.isDue(now, minuteOfDay(context, profile.id), prefs.getString(key(profile.id, "last_date"), null)) &&
                dao.hasLearningPlaylists(profile.id) && showNotification(context, profile.id)
            ) {
                prefs.edit().putString(key(profile.id, "last_date"), now.toLocalDate().toString()).commit()
            }
        }
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled includes POST_NOTIFICATIONS.
    private fun showNotification(context: Context, profileId: String): Boolean {
        val profile = ProfileManager.getProfile(context, profileId) ?: return false
        if (!LearningMode.isEnabled(context, profileId) || !isEnabled(context, profileId)) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.learning_reminders_title), NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
            .setContentTitle(context.getString(R.string.learning_reminder_notification_title, ProfileManager.getDisplayName(context, profile)))
            .setContentText(context.getString(R.string.learning_reminder_notification_text))
            .setContentIntent(PendingIntentCompat.getActivity(context, 0, intent(context, profileId), PendingIntent.FLAG_UPDATE_CURRENT, false))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        manager.notify(WORK + profileId, 0, notification)
        return true
    }

    fun cancelNotification(context: Context, profileId: String) {
        NotificationManagerCompat.from(context).cancel(WORK + profileId, 0)
    }
}
