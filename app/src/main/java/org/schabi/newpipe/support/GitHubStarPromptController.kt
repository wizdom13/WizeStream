package org.schabi.newpipe.support

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.util.external_communication.ShareUtils

class GitHubStarPromptController(private val activity: AppCompatActivity) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)

    fun onAppStart() {
        if (preferences.getBoolean(KEY_COMPLETED, false) ||
            preferences.getBoolean(KEY_DISMISSED, false)
        ) {
            return
        }

        val starts = preferences.getInt(KEY_APP_STARTS, 0) + 1
        preferences.edit().putInt(KEY_APP_STARTS, starts).apply()
        val nextPromptStart = preferences.getInt(KEY_NEXT_PROMPT_START, MINIMUM_APP_STARTS)
        if (!shouldPrompt(starts, nextPromptStart, completed = false, dismissed = false)) {
            return
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.github_star_prompt_title)
            .setMessage(R.string.github_star_prompt_message)
            .setPositiveButton(R.string.github_star_prompt_action) { _, _ ->
                preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
                openRepository(activity)
            }
            .setNeutralButton(R.string.github_star_prompt_never) { _, _ ->
                preferences.edit().putBoolean(KEY_DISMISSED, true).apply()
            }
            .setNegativeButton(R.string.app_update_later) { _, _ ->
                preferences.edit()
                    .putInt(KEY_NEXT_PROMPT_START, starts + REPROMPT_INTERVAL_STARTS)
                    .apply()
            }
            .show()
    }

    companion object {
        internal const val MINIMUM_APP_STARTS = 12
        internal const val REPROMPT_INTERVAL_STARTS = 12

        private const val KEY_APP_STARTS = "github_star_prompt_app_starts"
        private const val KEY_COMPLETED = "github_star_prompt_completed"
        private const val KEY_DISMISSED = "github_star_prompt_dismissed"
        private const val KEY_NEXT_PROMPT_START = "github_star_prompt_next_start"

        @JvmStatic
        fun openRepository(context: Context) {
            ShareUtils.openUrlInApp(context, context.getString(R.string.github_url))
        }

        internal fun shouldPrompt(
            starts: Int,
            nextPromptStart: Int,
            completed: Boolean,
            dismissed: Boolean
        ): Boolean = starts >= MINIMUM_APP_STARTS &&
            starts >= nextPromptStart &&
            !completed &&
            !dismissed
    }
}
