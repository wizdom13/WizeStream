package org.schabi.newpipe.extractor.services.peertube;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;

public class PeertubeParsingHelperTest {

    @Test
    public void acceptsOrdinaryApiPayload() throws Exception {
        final JsonObject json = JsonParser.object().from(
                "{\"name\":\"Available video\"}");

        PeertubeParsingHelper.validate(json);
    }

    @Test
    public void mapsApiMessageToContentNotAvailable() throws Exception {
        final JsonObject json = JsonParser.object().from(
                "{\"statusCode\":404,\"message\":\"Video not found\"}");

        final ContentNotAvailableException error = assertThrows(
                ContentNotAvailableException.class,
                () -> PeertubeParsingHelper.validate(json));

        assertTrue(error.getMessage().contains("Video not found"));
    }

    @Test
    public void mapsProblemDetailToContentNotAvailable() throws Exception {
        final JsonObject json = JsonParser.object().from(
                "{\"detail\":\"Remote video metadata is unavailable\"}");

        final ContentNotAvailableException error = assertThrows(
                ContentNotAvailableException.class,
                () -> PeertubeParsingHelper.validate(json));

        assertTrue(error.getMessage().contains("Remote video metadata is unavailable"));
    }
}
