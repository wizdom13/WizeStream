/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import java.util.Locale

object LearningNoteTime {
    @JvmStatic
    fun format(timestampMillis: Long): String {
        val totalSeconds = timestampMillis.coerceAtLeast(0) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    @JvmStatic
    fun parse(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size !in 2..3 || parts.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) {
            return null
        }
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        val hours = if (numbers.size == 3) numbers[0] else 0
        val minutes = numbers[numbers.size - 2]
        val seconds = numbers.last()
        if (minutes !in 0..59 || seconds !in 0..59) {
            return null
        }
        return runCatching {
            Math.addExact(
                Math.addExact(Math.multiplyExact(hours, 3_600_000), minutes * 60_000),
                seconds * 1_000
            )
        }.getOrNull()
    }
}
