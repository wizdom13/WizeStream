/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import androidx.annotation.Nullable;

import java.util.Locale;

import okhttp3.HttpUrl;

/** Normalizes user-entered PeerTube instance addresses to their HTTPS origin. */
public final class PeertubeInstanceUrl {
    private PeertubeInstanceUrl() {
    }

    @Nullable
    public static String normalize(@Nullable final String input) {
        if (input == null) {
            return null;
        }

        String candidate = input.trim();
        if (candidate.isEmpty()) {
            return null;
        }
        if (!hasScheme(candidate)) {
            candidate = "https://" + candidate;
        }

        final HttpUrl parsed;
        try {
            parsed = HttpUrl.get(candidate);
        } catch (final IllegalArgumentException error) {
            return null;
        }

        if (!"https".equals(parsed.scheme())
                || !parsed.username().isEmpty()
                || !parsed.password().isEmpty()) {
            return null;
        }

        final String origin = parsed.newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
                .toString();
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    public static boolean hasInsecureHttpScheme(@Nullable final String input) {
        return input != null
                && input.trim().toLowerCase(Locale.ROOT).startsWith("http://");
    }

    private static boolean hasScheme(final String input) {
        final int separator = input.indexOf(':');
        if (separator <= 0 || !Character.isLetter(input.charAt(0))) {
            return false;
        }

        for (int i = 1; i < separator; i++) {
            final char value = input.charAt(i);
            if (!Character.isLetterOrDigit(value)
                    && value != '+'
                    && value != '-'
                    && value != '.') {
                return false;
            }
        }
        return true;
    }
}
