/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerServiceStartPolicyTest {
    @Test
    void nullRestartStopsUnusedStartedService() {
        assertTrue(PlayerServiceStartPolicy.shouldStopStartedServiceOnNullIntent(false));
    }

    @Test
    void nullRestartPreservesActivePlayer() {
        assertFalse(PlayerServiceStartPolicy.shouldStopStartedServiceOnNullIntent(true));
    }
}
