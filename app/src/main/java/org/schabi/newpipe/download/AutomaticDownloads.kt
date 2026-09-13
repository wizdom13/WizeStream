package org.schabi.newpipe.download

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.schabi.newpipe.R

internal data class AutomaticDownloadRule(val uid: Long, val serviceId: Int, val url: String, val name: String, val audio: Boolean, val since: Long)

object AutomaticDownloads {
    const val ENABLED = "automatic_downloads_enabled"
    const val WIFI_ONLY = "automatic_downloads_wifi_only"
    private const val WORK = "automatic-channel-downloads"

    internal fun rules(context: Context): List<AutomaticDownloadRule> = context.getSharedPreferences(WORK, Context.MODE_PRIVATE).all.mapNotNull { (key, value) ->
        runCatching {
            val data = JSONObject(value as String)
            AutomaticDownloadRule(key.toLong(), data.getInt("service"), data.getString("url"), data.getString("name"), data.getBoolean("audio"), data.getLong("since"))
        }.getOrNull()
    }

    @JvmStatic
    fun initialize(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val manager = WorkManager.getInstance(context)
        if (!preferences.getBoolean(ENABLED, false) || rules(context).isEmpty()) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (preferences.getBoolean(WIFI_ONLY, true)) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutomaticDownloadWorker>(1, TimeUnit.HOURS).setConstraints(constraints).build()
        )
    }

    @JvmStatic
    fun configure(context: Context, uid: Long, serviceId: Int, url: String, name: String) {
        val current = rules(context).firstOrNull { it.uid == uid }
        val selected = when {
            current == null -> 0
            current.audio -> 1
            else -> 2
        }
        val options = arrayOf(context.getString(R.string.automatic_download_off), context.getString(R.string.audio), context.getString(R.string.video))
        MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.automatic_download_channel_title, name))
            .setSingleChoiceItems(options, selected) { dialog, index ->
                val editor = context.getSharedPreferences(WORK, Context.MODE_PRIVATE).edit()
                if (index == 0) {
                    editor.remove(uid.toString())
                } else {
                    editor.putString(
                        uid.toString(),
                        JSONObject().put("service", serviceId).put("url", url).put("name", name)
                            .put("audio", index == 1).put("since", current?.since ?: System.currentTimeMillis()).toString()
                    )
                    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(ENABLED, true).apply()
                }
                editor.apply()
                initialize(context)
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    @JvmStatic
    fun manage(context: Context) {
        val configured = rules(context)
        val builder = MaterialAlertDialogBuilder(context).setTitle(R.string.automatic_download_channels)
        if (configured.isEmpty()) {
            builder.setMessage(R.string.automatic_download_instructions).setPositiveButton(R.string.ok, null)
        } else {
            builder.setItems(configured.map { it.name }.toTypedArray()) { _, index ->
                val rule = configured[index]
                configure(context, rule.uid, rule.serviceId, rule.url, rule.name)
            }.setNegativeButton(R.string.cancel, null)
        }
        builder.show()
    }
}

internal object AutomaticDownloadPolicy {
    fun shouldDownload(publishedAt: Long?, enabledAt: Long, initialized: Boolean, seen: Boolean, live: Boolean): Boolean {
        if (seen || live) return false
        // The first check establishes a baseline rather than downloading a channel's archive.
        if (!initialized) return publishedAt != null && publishedAt >= enabledAt
        // Some feeds report only the date, so allow the enabling day after baseline creation.
        return publishedAt == null || publishedAt >= enabledAt - TimeUnit.DAYS.toMillis(1)
    }
}
