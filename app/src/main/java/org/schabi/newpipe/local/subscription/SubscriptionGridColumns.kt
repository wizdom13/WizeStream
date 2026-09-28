package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.preference.PreferenceManager

object SubscriptionGridColumns {
    const val AUTO = 0
    private const val KEY = "subscription_grid_columns"

    val presets = listOf(AUTO, 2, 3, 4, 5, 6)

    fun get(context: Context): Int = PreferenceManager.getDefaultSharedPreferences(context)
        .getInt(KEY, AUTO)
        .takeIf(presets::contains) ?: AUTO

    fun set(context: Context, columns: Int) {
        require(columns in presets)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putInt(KEY, columns)
            .apply()
    }
}
