/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.video

/** Percentages displayed in the editor; saturation 100 means unchanged. */
data class VideoAdjustmentState(
    val enabled: Boolean = false,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 100,
    val remember: Boolean = false
) {
    fun clamped() = copy(
        brightness = brightness.coerceIn(-100, 100),
        contrast = contrast.coerceIn(-100, 100),
        saturation = saturation.coerceIn(0, 200)
    )

    fun reset() = copy(brightness = 0, contrast = 0, saturation = 100)
}
