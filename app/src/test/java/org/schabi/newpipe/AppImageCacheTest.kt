/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppImageCacheTest {
    @Test
    fun `low-memory devices use a smaller bounded image cache`() {
        val lowMemoryPercent = App.imageMemoryCachePercent(true)
        val regularPercent = App.imageMemoryCachePercent(false)

        assertEquals(0.10, lowMemoryPercent, 0.0)
        assertEquals(0.20, regularPercent, 0.0)
        assertTrue(lowMemoryPercent < regularPercent)
    }
}
