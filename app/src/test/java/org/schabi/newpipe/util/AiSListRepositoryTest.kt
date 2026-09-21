/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiSListRepositoryTest {
    @Test
    fun `parser ignores metadata and normalizes entries`() {
        val entries = AiSListRepository.parse(
            """
            ! comment
            @ExampleChannel
            UCAbCdEf1234567890_xyz
            @examplechannel

            invalid
            """.trimIndent()
        )

        assertEquals(
            setOf("@examplechannel", "ucabcdef1234567890_xyz"),
            entries
        )
    }

    @Test
    fun `matches youtube handle URLs and channel IDs without using display names`() {
        val entries = setOf(
            "@blockedhandle",
            "ucabcdefghijklmnopqrstuv"
        )

        assertTrue(
            AiSListRepository.isListed(
                entries,
                "https://www.youtube.com/@BlockedHandle/videos",
                "Different display name"
            )
        )
        assertTrue(
            AiSListRepository.isListed(
                entries,
                "https://www.youtube.com/channel/UCABCDEFGHIJKLMNOPQRSTUV",
                "Different display name"
            )
        )
        assertFalse(
            AiSListRepository.isListed(
                entries,
                "https://www.youtube.com/@allowed",
                "BlockedHandle"
            )
        )
    }

    @Test
    fun `handle display value can match when extractor exposes it directly`() {
        assertTrue(
            AiSListRepository.isListed(
                setOf("@blockedhandle"),
                null,
                "@BlockedHandle"
            )
        )
    }
}
