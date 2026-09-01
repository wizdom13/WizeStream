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
    static final int COMPACT_DETAIL_TITLE_LINES = 1;
    static final int COMPACT_LOCAL_DETAIL_TITLE_LINES = 3;

    private GridTitleDisplayPolicy() {
    }

    public static void apply(final TextView titleView) {
        apply(titleView, COMPACT_TITLE_LINES, false);
    }

    public static void applyToDetail(final TextView titleView,
                                     final boolean manuallyExpanded) {
        apply(titleView, COMPACT_DETAIL_TITLE_LINES, manuallyExpanded);
    }

    public static void applyToLocalDetail(final TextView titleView) {
        apply(titleView, COMPACT_LOCAL_DETAIL_TITLE_LINES, false);
    }

    private static void apply(final TextView titleView,
                              final int compactTitleLines,
                              final boolean manuallyExpanded) {
        final boolean showFullTitles = PreferenceManager
                .getDefaultSharedPreferences(titleView.getContext())
                .getBoolean(titleView.getContext().getString(
                        R.string.show_full_grid_titles_key), false);

        titleView.setMaxLines(maxLines(showFullTitles, manuallyExpanded, compactTitleLines));
        titleView.setEllipsize(shouldEllipsize(showFullTitles, manuallyExpanded)
                ? TextUtils.TruncateAt.END : null);
    }

    static int maxLines(final boolean showFullTitles) {
        return maxLines(showFullTitles, false, COMPACT_TITLE_LINES);
    }

    static int maxLines(final boolean showFullTitles,
                        final boolean manuallyExpanded,
                        final int compactTitleLines) {
        return showFullTitles || manuallyExpanded ? Integer.MAX_VALUE : compactTitleLines;
    }

    static boolean shouldEllipsize(final boolean showFullTitles) {
        return shouldEllipsize(showFullTitles, false);
    }

    static boolean shouldEllipsize(final boolean showFullTitles,
                                   final boolean manuallyExpanded) {
        return !showFullTitles && !manuallyExpanded;
    }
}
