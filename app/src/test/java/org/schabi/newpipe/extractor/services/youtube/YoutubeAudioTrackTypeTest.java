package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class YoutubeAudioTrackTypeTest {
    @Test
    public void extractsKnownAudioTrackTypesFromXtags() {
        assertEquals(AudioTrackType.ORIGINAL,
                YoutubeParsingHelper.extractAudioTrackType(encodeXtags("original")));
        assertEquals(AudioTrackType.DUBBED,
                YoutubeParsingHelper.extractAudioTrackType(encodeXtags("dubbed")));
        assertEquals(AudioTrackType.DUBBED,
                YoutubeParsingHelper.extractAudioTrackType(encodeXtags("dubbed-auto")));
        assertEquals(AudioTrackType.DESCRIPTIVE,
                YoutubeParsingHelper.extractAudioTrackType(encodeXtags("descriptive")));
        assertEquals(AudioTrackType.SECONDARY,
                YoutubeParsingHelper.extractAudioTrackType(encodeXtags("secondary")));
    }

    @Test
    public void ignoresUnknownOrMalformedXtags() {
        assertNull(YoutubeParsingHelper.extractAudioTrackType(encodeXtags("commentary")));
        assertNull(YoutubeParsingHelper.extractAudioTrackType("not-base64!"));
        assertNull(YoutubeParsingHelper.extractAudioTrackType(null));
    }

    private static String encodeXtags(final String audioContentType) {
        final byte[] key = "acont".getBytes(StandardCharsets.UTF_8);
        final byte[] value = audioContentType.getBytes(StandardCharsets.UTF_8);

        final ByteArrayOutputStream pair = new ByteArrayOutputStream();
        pair.write(0x0A);
        pair.write(key.length);
        pair.writeBytes(key);
        pair.write(0x12);
        pair.write(value.length);
        pair.writeBytes(value);

        final byte[] pairBytes = pair.toByteArray();
        final ByteArrayOutputStream xtags = new ByteArrayOutputStream();
        xtags.write(0x0A);
        xtags.write(pairBytes.length);
        xtags.writeBytes(pairBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(xtags.toByteArray());
    }
}
