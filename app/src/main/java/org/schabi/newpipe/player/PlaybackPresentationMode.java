/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

/** Describes how active playback is presented without creating a second playback engine. */
public enum PlaybackPresentationMode {
    VIDEO(true, false),
    LISTEN_VISUALIZER(false, true),
    AUDIO_BACKGROUND(false, false),
    CAR_AUDIO(false, false);

    private final boolean rendersVideo;
    private final boolean allowsVisualizer;

    PlaybackPresentationMode(final boolean rendersVideo, final boolean allowsVisualizer) {
        this.rendersVideo = rendersVideo;
        this.allowsVisualizer = allowsVisualizer;
    }

    public boolean rendersVideo() {
        return rendersVideo;
    }

    public boolean allowsVisualizer() {
        return allowsVisualizer;
    }

    public boolean isAudioOnly() {
        return !rendersVideo;
    }
}
