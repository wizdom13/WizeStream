/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.helper

import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection

internal object PlaybackQualitySelection {
    // Use Media3's 10-second upgrade buffer instead of the former one-second override.
    // Its live-edge adjustment still permits upgrades when less live content is available.
    fun create(): AdaptiveTrackSelection.Factory = AdaptiveTrackSelection.Factory()
}
