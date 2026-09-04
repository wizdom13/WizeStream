/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.nio.file.Files;
import java.nio.file.Path;

public class LocalMediaLibraryNavigationTest {
    private final Path mainDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void browseAllSongsIsOnlyAvailableForLocalMedia() {
        final PlayQueueItem localItem = mock(PlayQueueItem.class);
        final PlayQueueItem remoteItem = mock(PlayQueueItem.class);
        when(localItem.isLocalMedia()).thenReturn(true);
        when(remoteItem.isLocalMedia()).thenReturn(false);

        assertTrue(PlayQueueActivity.shouldShowLocalMediaBrowser(localItem));
        assertFalse(PlayQueueActivity.shouldShowLocalMediaBrowser(remoteItem));
        assertFalse(PlayQueueActivity.shouldShowLocalMediaBrowser(null));
    }

    @Test
    public void playQueueRoutesBrowseActionToAudioTracksWithoutReplacingTheQueue()
            throws Exception {
        final String queueActivity = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/player/PlayQueueActivity.java"));
        final String mainActivity = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/MainActivity.java"));
        final String navigation = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/util/NavigationHelper.java"));
        final String localMedia = Files.readString(mainDirectory.resolve(
                "java/org/schabi/newpipe/local/media/LocalMediaFragment.kt"));

        assertTrue(queueActivity.contains("MainActivity.KEY_OPEN_LOCAL_MEDIA_AUDIO"));
        assertTrue(mainActivity.contains("openLocalMediaAudioFragment"));
        assertTrue(navigation.contains("LocalMediaFragment.newAudioTracksInstance()"));
        assertTrue(localMedia.contains("ARG_OPEN_AUDIO_TRACKS"));
        assertFalse(queueActivity.contains("new LocalMediaPlayQueue"));
    }
}
