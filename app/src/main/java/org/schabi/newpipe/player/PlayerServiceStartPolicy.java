/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

/** Defines how PlayerService should react when Android restarts it with a null intent. */
final class PlayerServiceStartPolicy {
    private PlayerServiceStartPolicy() {
    }

    static boolean shouldStopStartedServiceOnNullIntent(final boolean playerActive) {
        return !playerActive;
    }
}
