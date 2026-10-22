package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class YoutubeMusicAlbumLinkTest {
    @Test
    public void requiresAnExplicitAlbumEndpoint() throws Exception {
        final JsonArray runs = JsonParser.array().from("["
                + "{\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"UCartist\"}}},"
                + "{\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"MPREalbum\"}}}]");
        assertEquals("https://music.youtube.com/browse/MPREalbum",
                YoutubeMusicAlbumLink.fromSong(new JsonObject(), runs));
        assertNull(YoutubeMusicAlbumLink.fromSong(new JsonObject(), new JsonArray()));
        runs.remove(1);
        assertNull(YoutubeMusicAlbumLink.fromSong(new JsonObject(), runs));
    }

    @Test
    public void acceptsOnlyCanonicalAlbumPlaylists() throws Exception {
        final JsonObject album = JsonParser.object().from("{\"microformat\":{"
                + "\"microformatDataRenderer\":{\"urlCanonical\":"
                + "\"https://music.youtube.com/playlist?list=OLAK5uy_fixture\"}}}");
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_fixture",
                YoutubeMusicAlbumLink.playlistFromAlbum(album));
        assertNull(YoutubeMusicAlbumLink.playlistFromAlbum(new JsonObject()));
    }

    @Test
    public void queueItemsRetainAlbumMetadata() throws Exception {
        final org.schabi.newpipe.extractor.stream.StreamInfoItem track =
                new org.schabi.newpipe.extractor.stream.StreamInfoItem(0,
                        "https://music.youtube.com/watch?v=abcdefghijk", "Song",
                        org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM);
        track.setAlbumUrl("https://music.youtube.com/browse/MPREalbum");
        final org.schabi.newpipe.player.playqueue.PlayQueueItem item =
                new org.schabi.newpipe.player.playqueue.PlayQueueItem(track);
        assertEquals(track.getAlbumUrl(), item.getAlbumUrl());
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream output = new java.io.ObjectOutputStream(bytes)) {
            output.writeObject(item);
        }
        try (java.io.ObjectInputStream input = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(track.getAlbumUrl(),
                    ((org.schabi.newpipe.player.playqueue.PlayQueueItem) input.readObject())
                            .getAlbumUrl());
        }
    }
}
