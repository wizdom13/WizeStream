/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlaybackPresentationModeTest {
    @Test
    public void onlyVideoModeRendersVideo() {
        assertThat(PlaybackPresentationMode.VIDEO.rendersVideo()).isTrue();
        assertThat(PlaybackPresentationMode.LISTEN_VISUALIZER.rendersVideo()).isFalse();
        assertThat(PlaybackPresentationMode.AUDIO_BACKGROUND.rendersVideo()).isFalse();
        assertThat(PlaybackPresentationMode.CAR_AUDIO.rendersVideo()).isFalse();
    }

    @Test
    public void visualizerIsLimitedToVisibleListenMode() {
        assertThat(PlaybackPresentationMode.LISTEN_VISUALIZER.allowsVisualizer()).isTrue();
        assertThat(PlaybackPresentationMode.VIDEO.allowsVisualizer()).isFalse();
        assertThat(PlaybackPresentationMode.AUDIO_BACKGROUND.allowsVisualizer()).isFalse();
        assertThat(PlaybackPresentationMode.CAR_AUDIO.allowsVisualizer()).isFalse();
    }
}
