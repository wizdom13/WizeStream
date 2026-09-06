/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

/** Describes how active playback is presented without creating a second playback engine. */
enum class PlaybackPresentationMode(
    private val rendersVideoValue: Boolean,
    private val allowsVisualizerValue: Boolean
) {
    VIDEO(true, false),
    LISTEN_VISUALIZER(false, true),
    AUDIO_BACKGROUND(false, false),
    CAR_AUDIO(false, false);

    fun rendersVideo(): Boolean = rendersVideoValue

    fun allowsVisualizer(): Boolean = allowsVisualizerValue

    fun isAudioOnly(): Boolean = !rendersVideoValue
}
