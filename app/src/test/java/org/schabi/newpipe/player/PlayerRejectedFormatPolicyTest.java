package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.datasource.HttpDataSource;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.Collections;

public class PlayerRejectedFormatPolicyTest {
    @Test
    public void identifiesOnlyTheVideoItagRejectedWith403() throws Exception {
        final VideoStream video = new VideoStream.Builder()
                .setId("303").setContent("https://media.example.com/video", true)
                .setMediaFormat(MediaFormat.WEBM).setResolution("720p60")
                .setIsVideoOnly(true).setItagItem(ItagItem.getItag(303)).build();
        final IOException diagnostic = new IOException(
                "YouTube media request diagnostic: client=VISIONOS, cver=unknown,"
                        + " itag=303, userAgent=VISIONOS");
        assertTrue(PlayerHttpErrorRecovery.isRejectedVideoFormat(
                invalidResponseCodeException(403, diagnostic), video));
        assertFalse(PlayerHttpErrorRecovery.isRejectedVideoFormat(
                invalidResponseCodeException(404, diagnostic), video));
        assertFalse(PlayerHttpErrorRecovery.isRejectedVideoFormat(
                invalidResponseCodeException(403), video));
        assertFalse(PlayerHttpErrorRecovery.isRejectedVideoFormat(
                invalidResponseCodeException(403, new IOException(
                        "YouTube media request diagnostic: client=VISIONOS, itag=140")), video));
        assertFalse(PlayerHttpErrorRecovery.isRejectedVideoFormat(
                invalidResponseCodeException(403, new IOException(
                        "YouTube media request diagnostic: client=VISIONOS, itag=3030")), video));
        assertFalse(PlayerHttpErrorRecovery.isRejectedVideoFormat(
                invalidResponseCodeException(403, diagnostic), null));
    }


    private static HttpDataSource.InvalidResponseCodeException invalidResponseCodeException(
            final int responseCode) {
        return invalidResponseCodeException(responseCode, new IOException("HTTP error"));
    }

    private static HttpDataSource.InvalidResponseCodeException invalidResponseCodeException(
            final int responseCode, final IOException cause) {
        return new HttpDataSource.InvalidResponseCodeException(responseCode, "HTTP error",
                cause, Collections.emptyMap(), null, new byte[0]);
    }
}
