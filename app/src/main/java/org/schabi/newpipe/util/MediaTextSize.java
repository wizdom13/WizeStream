/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

/** Validated scaling for titles and channel names, relative to each layout's default. */
public final class MediaTextSize {
    private MediaTextSize() { }

    public static float scale(final String value) {
        if (value != null) {
            switch (value) {
                case "80":
                    return 0.8f;
                case "90":
                    return 0.9f;
                case "110":
                    return 1.1f;
                case "125":
                    return 1.25f;
                case "150":
                    return 1.5f;
                default:
                    break;
            }
        }
        return 1.0f;
    }
}
