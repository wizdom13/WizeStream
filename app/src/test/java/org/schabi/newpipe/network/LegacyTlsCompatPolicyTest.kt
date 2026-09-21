/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTlsCompatPolicyTest {
    @Test
    fun bundledRootIsLimitedToAndroidSixAndSevenZero() {
        assertFalse(LegacyTlsCompat.needsBundledIsrgRoot(22))
        assertTrue(LegacyTlsCompat.needsBundledIsrgRoot(23))
        assertTrue(LegacyTlsCompat.needsBundledIsrgRoot(24))
        assertFalse(LegacyTlsCompat.needsBundledIsrgRoot(25))
        assertFalse(LegacyTlsCompat.needsBundledIsrgRoot(35))
    }
}
