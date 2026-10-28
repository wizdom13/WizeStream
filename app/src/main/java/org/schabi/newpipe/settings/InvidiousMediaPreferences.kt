package org.schabi.newpipe.settings

import android.content.Context
import android.text.InputType
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousMediaOrigins
import org.schabi.newpipe.util.NavigationHelper

object InvidiousMediaPreferences {
    private fun key(instance: String) = "invidious_media_origins:$instance"

    @JvmStatic
    fun configure(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val instance = InvidiousBackend.getInstance()
            InvidiousMediaOrigins.configure(instance, prefs.getString(key(instance), "") ?: "")
        } catch (_: RuntimeException) {
            InvidiousMediaOrigins.clear()
        }
    }

    @JvmStatic
    fun bind(activity: FragmentActivity, preference: EditTextPreference, instance: String) {
        val context = preference.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val normalized = try {
            InvidiousBackend.normalizeInstance(instance)
        } catch (_: IllegalArgumentException) {
            null
        }
        preference.isPersistent = false
        preference.isEnabled = normalized != null
        preference.text = normalized?.let { prefs.getString(key(it), "") } ?: ""
        preference.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            it.setSingleLine(false)
            it.minLines = 3
        }
        preference.setOnPreferenceChangeListener { _, value ->
            try {
                val text = InvidiousMediaOrigins.parse(value as String).joinToString("\n")
                if (normalized != null && prefs.edit().putString(key(normalized), text).commit()) {
                    preference.text = text
                    if (prefs.getBoolean(context.getString(R.string.invidious_enabled_key), false)) NavigationHelper.restartApp(activity)
                }
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.invidious_media_origins_invalid, Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
