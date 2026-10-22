package org.schabi.newpipe.settings

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale

object PreferredPlaybackLanguages {
    const val KEY = "preferred_playback_languages"

    @JvmStatic
    fun allows(context: Context, language: String?): Boolean = matches(
        language,
        PreferenceManager.getDefaultSharedPreferences(context).getStringSet(KEY, emptySet()).orEmpty()
    )

    // Caption formats currently carry display names, while audio streams carry BCP-47 tags.
    @JvmStatic
    fun matches(language: String?, preferred: Set<String>): Boolean {
        if (preferred.isEmpty()) return true
        if (language.isNullOrBlank()) return true // Keep unlabelled/original tracks reachable.
        val normalized = language.trim().replace('_', '-').lowercase(Locale.ROOT)
        return preferred.any { code ->
            val locale = Locale.forLanguageTag(code)
            normalized == code.lowercase(Locale.ROOT) ||
                normalized.startsWith("${code.lowercase(Locale.ROOT)}-") ||
                listOf(Locale.ENGLISH, Locale.getDefault(), locale).any { displayLocale ->
                    normalized == locale.getDisplayLanguage(displayLocale).lowercase(Locale.ROOT)
                }
        }
    }
}
