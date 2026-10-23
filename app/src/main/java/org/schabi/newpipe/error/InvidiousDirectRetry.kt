package org.schabi.newpipe.error

import android.app.Activity
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.processphoenix.ProcessPhoenix
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend
import org.schabi.newpipe.util.NavigationHelper

/** An explicit backend change, never an automatic exception to the Invidious network policy. */
object InvidiousDirectRetry {
    @JvmStatic
    fun isAvailable(serviceId: Int, url: String?): Boolean = InvidiousBackend.isEnabled() &&
        serviceId == ServiceList.YouTube.serviceId && !url.isNullOrBlank() &&
        runCatching { ServiceList.YouTube.streamLHFactory.acceptUrl(url) }.getOrDefault(false)

    @JvmStatic
    fun confirm(activity: Activity, serviceId: Int, url: String?, title: String?) {
        if (!isAvailable(serviceId, url) || activity.isFinishing) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.invidious_direct_retry)
            .setMessage(R.string.invidious_direct_retry_warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.invidious_direct_switch) { _, _ ->
                if (!isAvailable(serviceId, url)) return@setPositiveButton
                val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
                if (!preferences.edit().putBoolean(activity.getString(R.string.invidious_enabled_key), false).commit()) {
                    Toast.makeText(activity, R.string.invidious_direct_save_failed, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                // Retire existing clients, caches and media sources before changing the backend.
                // Keep the instance configuration so users can re-enable it in Content settings.
                val intent = NavigationHelper.getStreamIntent(activity, serviceId, url, title)
                NewPipeDatabase.close()
                ProcessPhoenix.triggerRebirth(activity.applicationContext, intent)
            }.show()
    }
}
