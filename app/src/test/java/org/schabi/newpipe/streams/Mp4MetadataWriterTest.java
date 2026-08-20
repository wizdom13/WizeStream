package org.schabi.newpipe.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Mp4MetadataWriterTest {
    @Test
    public void createsSizedUdtaAndMetaAtomsWithUtf8Values() {
        final MediaTagMetadata metadata = new MediaTagMetadata(
                "Café session", "Channel Artist", "Music", "2026-08-20",
                "https://example.com/watch/1");

        final byte[] result = Mp4MetadataWriter.makeUdta(metadata);

        assertEquals(result.length, readInt(result, 0));
        assertEquals(0x75647461, readInt(result, 4));
        assertEquals(result.length - 8, readInt(result, 8));
        assertEquals(0x6D657461, readInt(result, 12));

        final String binaryText = new String(result, StandardCharsets.ISO_8859_1);
        assertTrue(binaryText.contains("CafÃ© session"));
        assertTrue(binaryText.contains("Channel Artist"));
        assertTrue(binaryText.contains("2026-08-20"));
        assertTrue(binaryText.contains("https://example.com/watch/1"));
    }

    @Test
    public void skipsBlankMetadataValues() {
        final byte[] result = Mp4MetadataWriter.makeUdta(
                new MediaTagMetadata("Title", "Artist", "  ", null, null));
        final String binaryText = new String(result, StandardCharsets.ISO_8859_1);

        assertTrue(binaryText.contains("Title"));
        assertTrue(binaryText.contains("Artist"));
        assertFalse(containsInt(result, 0xA967656E));
    }

    private static int readInt(final byte[] source, final int offset) {
        return ByteBuffer.wrap(source, offset, Integer.BYTES).getInt();
    }

    private static boolean containsInt(final byte[] source, final int value) {
        for (int offset = 0; offset <= source.length - Integer.BYTES; offset++) {
            if (readInt(source, offset) == value) {
                return true;
            }
        }
        return false;
    }
}
