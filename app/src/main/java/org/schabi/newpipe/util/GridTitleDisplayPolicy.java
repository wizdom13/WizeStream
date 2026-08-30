/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.text.TextUtils;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

public final class GridTitleDisplayPolicy {
    static final int COMPACT_TITLE_LINES = 2;

    private GridTitleDisplayPolicy() {
    }

    public static void apply(final TextView titleView) {
        final boolean showFullTitles = PreferenceManager
                .getDefaultSharedPreferences(titleView.getContext())
                .getBoolean(titleView.getContext().getString(
                        R.string.show_full_grid_titles_key), false);

        titleView.setMaxLines(maxLines(showFullTitles));
        titleView.setEllipsize(shouldEllipsize(showFullTitles)
                ? TextUtils.TruncateAt.END : null);
    }

    static int maxLines(final boolean showFullTitles) {
        return showFullTitles ? Integer.MAX_VALUE : COMPACT_TITLE_LINES;
    }

    static boolean shouldEllipsize(final boolean showFullTitles) {
        return !showFullTitles;
    }
}
