/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

internal object DeviceSyncListenerPolicy {
    fun shouldRun(backgroundSyncEnabled: Boolean, hasTrustedPeers: Boolean): Boolean {
        return backgroundSyncEnabled && hasTrustedPeers
    }
}
