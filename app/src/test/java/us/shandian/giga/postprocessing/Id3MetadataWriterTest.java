package us.shandian.giga.postprocessing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Id3MetadataWriterTest {
    @Test
    void writesTitleArtistAndSourceUrlFrames() throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        Id3MetadataWriter.write(output, "Title", "Uploader", "https://example.test/video");

        final byte[] bytes = output.toByteArray();
        assertEquals("ID3", new String(bytes, 0, 3, StandardCharsets.US_ASCII));
        assertEquals(4, bytes[3]);
        final String tag = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(tag.contains("TIT2"));
        assertTrue(tag.contains("TPE1"));
        assertTrue(tag.contains("WXXX"));
        assertTrue(tag.contains("https://example.test/video"));
    }
}
