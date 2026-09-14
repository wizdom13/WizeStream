package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YoutubePlaylistTitleTest {
    private static final String ALBUM_ID = "OLAK5uy_l9LKdjNgw3T8rq-dvNMuIvNtSsGDjpPa0";
    private static final String ALBUM_TITLE = "Titanic: Music from the Motion Picture Soundtrack";

    @Test
    void removesGeneratedLabelFromSidebarTitle() throws Exception {
        // Title fields from this album's YouTube playlist response with hl=zu.
        final JsonObject primaryInfo = JsonParser.object().from("""
                {"title":{"runs":[{
                  "text":"I-albhamu - Titanic: Music from the Motion Picture Soundtrack"
                }]}}
                """);

        assertEquals(ALBUM_TITLE, YoutubePlaylistExtractor.extractName(
                ALBUM_ID, primaryInfo, new JsonObject()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":{\"simpleText\":\"\"}}"})
    void removesGeneratedLabelFromFallbackTitle(final String sidebar) throws Exception {
        final JsonObject response = JsonParser.object().from("""
                {"microformat":{"microformatDataRenderer":{
                  "title":"I-albhamu - Titanic: Music from the Motion Picture Soundtrack"
                }}}
                """);

        assertEquals(ALBUM_TITLE, YoutubePlaylistExtractor.extractName(
                ALBUM_ID, JsonParser.object().from(sidebar), response));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PL1234567890", "RD1234567890", "PL_OLAK5uy_1234567890"})
    void preservesOrdinaryPlaylistAndMixTitles(final String playlistId) throws Exception {
        final String name = "I-albhamu - My favorites";
        final JsonObject primaryInfo = primaryInfo(name);
        final JsonObject response = response(name);

        assertEquals(name, YoutubePlaylistExtractor.extractName(
                playlistId, primaryInfo, response));
        assertEquals(name, YoutubePlaylistExtractor.extractName(
                playlistId, new JsonObject(), response));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Titanic", "Album - My favorites", "My I-albhamu - collection",
            "I-albhamu", "I-albhamu - "})
    void preservesTitlesWithoutARemovableLabel(final String name) throws Exception {
        assertEquals(name, YoutubePlaylistExtractor.extractName(
                ALBUM_ID, primaryInfo(name), response(name)));
    }

    @Test
    void removesOnlyOneLeadingLabelAndPreservesTheRestOfTheTitle() throws Exception {
        final String title = "I-albhamu - أغاني - Live (2026)";

        assertEquals(title, YoutubePlaylistExtractor.extractName(
                ALBUM_ID, primaryInfo("I-albhamu - " + title), new JsonObject()));
    }

    @Test
    void prefersSidebarTitleOverFallback() throws Exception {
        assertEquals("Sidebar album title", YoutubePlaylistExtractor.extractName(
                ALBUM_ID, primaryInfo("I-albhamu - Sidebar album title"),
                response("I-albhamu - Fallback album title")));
    }

    private static JsonObject primaryInfo(final String name) {
        final JsonObject title = new JsonObject();
        title.put("simpleText", name);
        final JsonObject primaryInfo = new JsonObject();
        primaryInfo.put("title", title);
        return primaryInfo;
    }

    private static JsonObject response(final String name) {
        final JsonObject renderer = new JsonObject();
        renderer.put("title", name);
        final JsonObject microformat = new JsonObject();
        microformat.put("microformatDataRenderer", renderer);
        final JsonObject response = new JsonObject();
        response.put("microformat", microformat);
        return response;
    }
}
