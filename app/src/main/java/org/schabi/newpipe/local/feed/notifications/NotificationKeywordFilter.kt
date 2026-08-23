/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import java.util.Locale
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Literal, case-insensitive title matching for per-channel notification filters. */
object NotificationKeywordFilter {
    const val MAX_TERMS = 25
    const val MAX_TERM_LENGTH = 100
    const val MAX_ENCODED_LENGTH = 2048

    fun normalize(input: String): String = input.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .joinToString("\n")

    fun terms(encoded: String): List<String> = normalize(encoded).lineSequence()
        .filter(String::isNotEmpty)
        .toList()

    fun isValid(encoded: String): Boolean {
        val parsed = terms(encoded)
        return encoded.length <= MAX_ENCODED_LENGTH &&
            parsed.isNotEmpty() &&
            parsed.size <= MAX_TERMS &&
            parsed.all { it.length <= MAX_TERM_LENGTH }
    }

    fun matches(title: String, encoded: String): Boolean {
        val normalizedTitle = title.lowercase(Locale.ROOT)
        return terms(encoded).any { term ->
            normalizedTitle.contains(term.lowercase(Locale.ROOT))
        }
    }

    fun filter(
        streams: List<StreamInfoItem>,
        @NotificationMode mode: Int,
        encoded: String
    ): List<StreamInfoItem> = when (mode) {
        NotificationMode.ENABLED -> streams
        NotificationMode.KEYWORDS_ONLY -> streams.filter { matches(it.name, encoded) }
        else -> emptyList()
    }
}
