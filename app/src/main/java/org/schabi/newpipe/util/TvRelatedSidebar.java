/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/** Configures the television recommendation column without changing tablet layouts. */
public final class TvRelatedSidebar {
    private TvRelatedSidebar() { }

    public static void apply(final View mainContent, final View sidebar) {
        final Context context = sidebar.getContext();
        final float share = sidebarShare(DeviceUtils.isTv(context),
                PreferenceManager.getDefaultSharedPreferences(context).getString(
                        context.getString(R.string.tv_related_sidebar_width_key), "compact"));
        final LinearLayout.LayoutParams mainParams =
                (LinearLayout.LayoutParams) mainContent.getLayoutParams();
        final LinearLayout.LayoutParams sidebarParams =
                (LinearLayout.LayoutParams) sidebar.getLayoutParams();
        mainParams.weight = 1.0f - share;
        sidebarParams.weight = share;
        mainContent.setLayoutParams(mainParams);
        sidebar.setLayoutParams(sidebarParams);
    }

    static float sidebarShare(final boolean television, final String value) {
        if (!television || "wide".equals(value)) {
            return 4.0f / 9.0f;
        }
        if ("balanced".equals(value)) {
            return 1.0f / 3.0f;
        }
        return 0.25f;
    }
}
