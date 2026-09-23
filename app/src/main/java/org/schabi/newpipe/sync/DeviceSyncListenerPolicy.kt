/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

internal object DeviceSyncListenerPolicy {
    private const val FOREGROUND_SERVICE_START_NOT_ALLOWED =
        "android.app.ForegroundServiceStartNotAllowedException"

    fun shouldRun(backgroundSyncEnabled: Boolean, hasTrustedPeers: Boolean): Boolean {
        return backgroundSyncEnabled && hasTrustedPeers
    }

    fun isForegroundPromotionRejected(exceptionClassName: String): Boolean {
        return exceptionClassName == FOREGROUND_SERVICE_START_NOT_ALLOWED
    }
}
