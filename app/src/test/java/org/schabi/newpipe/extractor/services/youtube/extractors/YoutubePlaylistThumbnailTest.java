package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

class YoutubePlaylistThumbnailTest {
    private static final String SIDEBAR_ARTWORK =
            "https://i.ytimg.com/vi/sidebar/maxresdefault.jpg";
    private static final String HEADER_ARTWORK =
            "https://i.ytimg.com/vi/header/maxresdefault.jpg";
    private static final String MICROFORMAT_ARTWORK =
            "https://i.ytimg.com/vi/microformat/maxresdefault.jpg";

    @Test
    void detectsHeaderOnlyPlaylistInterface() throws Exception {
        assertTrue(YoutubePlaylistExtractor.hasNewPlaylistInterface(
                JsonParser.object().from("{\"header\":{\"playlistHeaderRenderer\":{}}}")));
        assertFalse(YoutubePlaylistExtractor.hasNewPlaylistInterface(
                JsonParser.object().from(
                        "{\"header\":{},\"sidebar\":{\"playlistSidebarRenderer\":{}}}"
                )));
        assertFalse(YoutubePlaylistExtractor.hasNewPlaylistInterface(new JsonObject()));
    }

    @Test
    void extractsArtworkFromClassicSidebarMetadata() throws Exception {
        final JsonObject primaryInfo = JsonParser.object().from("""
                {
                  "thumbnailRenderer": {
                    "playlistVideoThumbnailRenderer": {
                      "thumbnail": {
                        "thumbnails": [
                          {"url": "https://i.ytimg.com/vi/sidebar/default.jpg"},
                          {"url": "https://i.ytimg.com/vi/sidebar/maxresdefault.jpg"}
                        ]
                      }
                    }
                  }
                }
                """);

        assertEquals(
                SIDEBAR_ARTWORK,
                YoutubePlaylistExtractor.extractThumbnailUrl(
                        primaryInfo,
                        new JsonObject(),
                        false
                )
        );
    }

    @Test
    void extractsArtworkFromNewPlaylistHeaderForMusicAlbum() throws Exception {
        final JsonObject response = JsonParser.object().from("""
                {
                  "header": {
                    "playlistHeaderRenderer": {
                      "playlistHeaderBanner": {
                        "heroPlaylistThumbnailRenderer": {
                          "thumbnail": {
                            "thumbnails": [
                              {"url": "https://i.ytimg.com/vi/header/default.jpg"},
                              {"url": "https://i.ytimg.com/vi/header/maxresdefault.jpg"}
                            ]
                          }
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals(
                HEADER_ARTWORK,
                YoutubePlaylistExtractor.extractThumbnailUrl(
                        new JsonObject(),
                        response,
                        true
                )
        );
    }

    @Test
    void usesHeaderArtworkWhenBothLayoutsExistButSidebarArtworkIsMissing() throws Exception {
        final JsonObject response = JsonParser.object().from("""
                {
                  "sidebar": {"playlistSidebarRenderer": {}},
                  "header": {
                    "playlistHeaderRenderer": {
                      "playlistHeaderBanner": {
                        "heroPlaylistThumbnailRenderer": {
                          "thumbnail": {
                            "thumbnails": [
                              {"url": "https://i.ytimg.com/vi/header/maxresdefault.jpg"}
                            ]
                          }
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals(
                HEADER_ARTWORK,
                YoutubePlaylistExtractor.extractThumbnailUrl(
                        new JsonObject(),
                        response,
                        false
                )
        );
    }

    @Test
    void retainsMicroformatArtworkFallback() throws Exception {
        final JsonObject response = JsonParser.object().from("""
                {
                  "microformat": {
                    "microformatDataRenderer": {
                      "thumbnail": {
                        "thumbnails": [
                          {"url": "https://i.ytimg.com/vi/microformat/maxresdefault.jpg"}
                        ]
                      }
                    }
                  }
                }
                """);

        assertEquals(
                MICROFORMAT_ARTWORK,
                YoutubePlaylistExtractor.extractThumbnailUrl(
                        new JsonObject(),
                        response,
                        false
                )
        );
    }

    @Test
    void failsClearlyWhenNoPlaylistArtworkExists() {
        assertThrows(
                ParsingException.class,
                () -> YoutubePlaylistExtractor.extractThumbnailUrl(
                        new JsonObject(),
                        new JsonObject(),
                        false
                )
        );
    }
}
