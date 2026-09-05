/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class LocalPlaylistShuffleIntegrationTest {
    private final Path mainDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void localPlaylistMenuStartsTheCompletePlaylistShuffled() throws Exception {
        final String fragment = read(
                "java/org/schabi/newpipe/local/playlist/LocalPlaylistFragment.java");
        final String menu = read("res/menu/menu_local_playlist.xml");

        assertTrue(menu.contains("@+id/menu_item_shuffle_playlist"));
        assertTrue(fragment.contains("getCompletePlaylistPlayQueue()"));
        assertTrue(fragment.contains("playQueue.shuffleFromStart()"));
        assertTrue(fragment.contains("contextualSearchQuery) ? unfilteredItems"));
    }

    @Test
    public void playerRestoresTheQueueShuffleState() throws Exception {
        final String player = read("java/org/schabi/newpipe/player/Player.java");
        final String queueModeController = read(
                "java/org/schabi/newpipe/player/PlayerQueueModeController.kt");

        assertTrue(player.contains(
                "simpleExoPlayer.setShuffleModeEnabled(playQueue.isShuffled())"));
        assertTrue(queueModeController.contains("shuffleModeEnabled && !queue.isShuffled"));
        assertTrue(queueModeController.contains("!shuffleModeEnabled && queue.isShuffled"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(mainDirectory.resolve(relativePath));
    }
}
