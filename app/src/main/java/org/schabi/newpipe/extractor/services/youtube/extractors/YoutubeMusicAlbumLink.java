package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

/** Uses explicit album endpoints, never infers an album from the track or artist title. */
public final class YoutubeMusicAlbumLink {
    private YoutubeMusicAlbumLink() {
    }

    public static String fromSong(final JsonObject song, final JsonArray description) {
        for (final Object run : description) {
            if (run instanceof JsonObject) {
                final String url = fromEndpoint(((JsonObject) run).getObject("navigationEndpoint"));
                if (url != null) {
                    return url;
                }
            }
        }
        for (final Object entry : song.getObject("menu").getObject("menuRenderer")
                .getArray("items")) {
            if (!(entry instanceof JsonObject)) {
                continue;
            }
            final JsonObject renderer = ((JsonObject) entry).getObject("menuNavigationItemRenderer");
            if ("ALBUM".equals(renderer.getObject("icon").getString("iconType"))) {
                final String url = fromEndpoint(renderer.getObject("navigationEndpoint"));
                if (url != null) {
                    return url;
                }
            }
        }
        return null;
    }

    private static String fromEndpoint(final JsonObject endpoint) {
        final String browseId = endpoint.getObject("browseEndpoint").getString("browseId", "");
        if (browseId.matches("MPRE[a-zA-Z0-9_-]+")) {
            return "https://music.youtube.com/browse/" + browseId;
        }
        return null;
    }

    public static String playlistFromAlbum(final JsonObject response) {
        final String canonical = response.getObject("microformat")
                .getObject("microformatDataRenderer").getString("urlCanonical", "");
        if (canonical.matches("https://(?:music\\.|www\\.)?youtube\\.com/playlist"
                + "\\?list=OLAK5uy_[a-zA-Z0-9_-]+")) {
            return canonical;
        }
        return null;
    }
}
