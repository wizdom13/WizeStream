/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncBackgroundPolicyTest {
    @Test
    fun `automatic work requires the setting and a trusted peer`() {
        assertTrue(
            DeviceSyncBackgroundPolicy.shouldSchedule(
                enabled = true,
                hasTrustedPeers = true
            )
        )
        assertFalse(
            DeviceSyncBackgroundPolicy.shouldSchedule(
                enabled = false,
                hasTrustedPeers = true
            )
        )
        assertFalse(
            DeviceSyncBackgroundPolicy.shouldSchedule(
                enabled = true,
                hasTrustedPeers = false
            )
        )
    }

    @Test
    fun `only local network transports are eligible`() {
        assertTrue(DeviceSyncBackgroundPolicy.hasLocalTransport(wifi = true, ethernet = false))
        assertTrue(DeviceSyncBackgroundPolicy.hasLocalTransport(wifi = false, ethernet = true))
        assertFalse(DeviceSyncBackgroundPolicy.hasLocalTransport(wifi = false, ethernet = false))
    }
}
