/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackPresentationModeTest {
    @Test
    public void onlyVideoModeRendersVideo() {
        assertTrue(PlaybackPresentationMode.VIDEO.rendersVideo());
        assertFalse(PlaybackPresentationMode.LISTEN_VISUALIZER.rendersVideo());
        assertFalse(PlaybackPresentationMode.AUDIO_BACKGROUND.rendersVideo());
        assertFalse(PlaybackPresentationMode.CAR_AUDIO.rendersVideo());
    }

    @Test
    public void visualizerIsLimitedToVisibleListenMode() {
        assertTrue(PlaybackPresentationMode.LISTEN_VISUALIZER.allowsVisualizer());
        assertFalse(PlaybackPresentationMode.VIDEO.allowsVisualizer());
        assertFalse(PlaybackPresentationMode.AUDIO_BACKGROUND.allowsVisualizer());
        assertFalse(PlaybackPresentationMode.CAR_AUDIO.allowsVisualizer());
    }
}
