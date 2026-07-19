@file:Suppress("ktlint:standard:filename", "ktlint:standard:class-naming")

package org.schabi.newpipe.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import java.io.InputStream
import org.schabi.newpipe.R

/**
 * Applies the canonical WizeStream default SharedPreferences snapshot.
 */
object wizestreamDefaultPreferences {
    const val DEFAULTS_APPLIED_KEY = "wizestream_defaults_applied"

    /**
     * Apply the bundled WizeStream default preferences.
     *
     * @param clearFirst when true, clears existing preferences before applying the snapshot.
     */
    @JvmStatic
    fun applyBundledDefaults(context: Context, clearFirst: Boolean = false) {
        context.resources.openRawResource(R.raw.wizestream_default_preferences).use { input ->
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            applyDefaults(input, preferences, clearFirst)
        }
    }

    /**
     * Apply defaults on a fresh install without overwriting preferences for existing users.
     */
    @JvmStatic
    fun applyBundledDefaultsIfNeeded(context: Context, isFirstRun: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (
            !isFirstRun ||
            preferences.getBoolean(DEFAULTS_APPLIED_KEY, false) ||
            preferences.all.isNotEmpty()
        ) {
            return
        }

        context.resources.openRawResource(R.raw.wizestream_default_preferences).use { input ->
            applyDefaults(input, preferences, clearFirst = false)
        }
    }

    @JvmStatic
    fun applyDefaults(
        input: InputStream,
        preferences: SharedPreferences,
        clearFirst: Boolean = false
    ) {
        val jsonObject = JsonParser.`object`().from(input)
        val editor = preferences.edit()
        if (clearFirst) {
            editor.clear()
        }

        for ((key, value) in jsonObject) {
            putValue(editor, key, value)
        }
        editor.putBoolean(DEFAULTS_APPLIED_KEY, true)

        if (!editor.commit()) {
            throw IllegalStateException("Unable to commit WizeStream default preferences")
        }
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)

            is String -> editor.putString(key, value)

            is Int -> editor.putInt(key, value)

            is Long -> editor.putLong(key, value)

            is Float -> editor.putFloat(key, value)

            is Double -> editor.putFloat(key, value.toFloat())

            is JsonArray -> editor.putStringSet(key, value.toStringSet(key))

            else -> throw IllegalArgumentException(
                "Unsupported WizeStream default preference type for key: $key"
            )
        }
    }

    private fun JsonArray.toStringSet(key: String): Set<String> = mapIndexed { index, item ->
        item as? String ?: throw IllegalArgumentException(
            "Unsupported non-string array item at $key[$index]"
        )
    }.toSet()
}
