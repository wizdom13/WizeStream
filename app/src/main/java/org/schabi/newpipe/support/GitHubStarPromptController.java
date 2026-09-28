package org.schabi.newpipe.support;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public final class GitHubStarPromptController {
    static final int MINIMUM_APP_STARTS = 12;

    private static final String KEY_APP_STARTS = "github_star_prompt_app_starts";
    private static final String KEY_COMPLETED = "github_star_prompt_completed";
    private static final String KEY_DISMISSED = "github_star_prompt_dismissed";

    private final AppCompatActivity activity;
    private final SharedPreferences preferences;

    public GitHubStarPromptController(@NonNull final AppCompatActivity activity) {
        this.activity = activity;
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    public void onAppStart() {
        if (preferences.getBoolean(KEY_COMPLETED, false)
                || preferences.getBoolean(KEY_DISMISSED, false)) {
            return;
        }

        final int starts = preferences.getInt(KEY_APP_STARTS, 0) + 1;
        preferences.edit().putInt(KEY_APP_STARTS, starts).apply();
        if (!shouldPrompt(starts, false, false)) {
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.github_star_prompt_title)
                .setMessage(R.string.github_star_prompt_message)
                .setPositiveButton(R.string.github_star_prompt_action, (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_COMPLETED, true).apply();
                    ShareUtils.openUrlInApp(activity, activity.getString(R.string.github_url));
                })
                .setNeutralButton(R.string.github_star_prompt_never, (dialog, which) ->
                        preferences.edit().putBoolean(KEY_DISMISSED, true).apply())
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    public static void openRepository(@NonNull final Context context) {
        ShareUtils.openUrlInApp(context, context.getString(R.string.github_url));
    }

    static boolean shouldPrompt(final int starts,
                                final boolean completed,
                                final boolean dismissed) {
        return starts >= MINIMUM_APP_STARTS && !completed && !dismissed;
    }

    private GitHubStarPromptController() {
        throw new AssertionError("No instances");
    }
}
