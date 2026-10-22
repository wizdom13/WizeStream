package org.schabi.newpipe.local.history

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Device-wide history suppression; deliberately independent of service and profile. */
object IncognitoMode {
    const val KEY = "incognito_mode"

    @JvmStatic
    fun isEnabled(context: Context): Boolean = isEnabled(PreferenceManager.getDefaultSharedPreferences(context))

    @JvmStatic
    fun isEnabled(preferences: SharedPreferences): Boolean = preferences.getBoolean(KEY, false)

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY, enabled).apply()
    }
}
