package org.schabi.newpipe.local;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class LocalUploaderNavigationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourceDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void enablesOnlyRemoteStreamsWithEnoughChannelLookupMetadata() {
        assertTrue(LocalUploaderNavigation.canOpenChannel(
                false, "https://example.com/watch?v=1", "Uploader"));
        assertFalse(LocalUploaderNavigation.canOpenChannel(
                true, "content://media/video/1", "Local folder"));
        assertFalse(LocalUploaderNavigation.canOpenChannel(false, "", "Uploader"));
        assertFalse(LocalUploaderNavigation.canOpenChannel(
                false, "https://example.com/watch?v=1", "  "));
    }

    @Test
    public void historyAndLocalPlaylistsBindAndReleaseUploaderNavigation() throws Exception {
        final String history = read(
                "org/schabi/newpipe/local/history/StatisticsPlaylistFragment.java");
        final String playlist = read(
                "org/schabi/newpipe/local/playlist/LocalPlaylistFragment.java");
        final String historyHolder = read(
                "org/schabi/newpipe/local/holder/LocalStatisticStreamItemHolder.java");
        final String playlistHolder = read(
                "org/schabi/newpipe/local/holder/LocalPlaylistStreamItemHolder.java");
        final String baseHolder = read(
                "org/schabi/newpipe/local/holder/LocalItemHolder.java");

        assertTrue(history.contains("setUploaderSelectedListener"));
        assertTrue(history.contains("unsetUploaderSelectedListener"));
        assertTrue(playlist.contains("setUploaderSelectedListener"));
        assertTrue(playlist.contains("unsetUploaderSelectedListener"));
        assertTrue(historyHolder.contains("bindUploaderNavigation"));
        assertTrue(playlistHolder.contains("bindUploaderNavigation"));
        assertTrue(baseHolder.contains("uploaderRoot.setOnClickListener(null)"));
        assertTrue(baseHolder.contains("uploaderRoot.setClickable(false)"));
        assertUploaderRipple("list_stream_playlist_item.xml");
        assertUploaderRipple("list_stream_playlist_grid_item.xml");
        assertUploaderRipple("list_stream_playlist_card_item.xml");
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }

    private void assertUploaderRipple(final String fileName) throws Exception {
        final String layout = Files.readString(
                resourceDirectory.resolve("layout").resolve(fileName));
        assertTrue(layout.contains("android:id=\"@+id/itemUploaderRoot\""));
        assertTrue(layout.contains(
                "android:background=\"?attr/selectableItemBackgroundBorderless\""));
    }
}
