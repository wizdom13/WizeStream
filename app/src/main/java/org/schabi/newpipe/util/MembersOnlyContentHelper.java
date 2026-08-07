/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;

/**
 * Centralizes the members-only visibility preference and its playback explanation.
 */
public final class MembersOnlyContentHelper {
    private MembersOnlyContentHelper() { }

    /**
     * @param context the Android context
     * @return whether membership-restricted videos should be omitted from content lists
     */
    public static boolean shouldHide(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.hide_members_only_videos_key), false);
    }

    /**
     * Explains why WizeStream cannot play a membership-restricted video.
     *
     * @param context the Android context
     */
    public static void showExplanation(final Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.members_only)
                .setMessage(R.string.members_only_explanation)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
