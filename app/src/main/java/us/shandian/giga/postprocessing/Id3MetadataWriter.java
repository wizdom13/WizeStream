package us.shandian.giga.postprocessing;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class Id3MetadataWriter {
    private Id3MetadataWriter() {
    }

    static void write(final OutputStream output,
                      @Nullable final String title,
                      @Nullable final String artist,
                      @Nullable final String sourceUrl) throws IOException {
        final ByteArrayOutputStream frames = new ByteArrayOutputStream();
        writeTextFrame(frames, "TIT2", title);
        writeTextFrame(frames, "TPE1", artist);
        writeUrlFrame(frames, sourceUrl);

        final byte[] payload = frames.toByteArray();
        output.write(new byte[] {'I', 'D', '3', 4, 0, 0});
        output.write(toSynchsafe(payload.length));
        output.write(payload);
    }

    private static void writeTextFrame(final OutputStream output,
                                       final String id,
                                       @Nullable final String value) throws IOException {
        if (value == null || value.isBlank()) {
            return;
        }
        final byte[] text = value.getBytes(StandardCharsets.UTF_8);
        writeFrameHeader(output, id, text.length + 1);
        output.write(3);
        output.write(text);
    }

    private static void writeUrlFrame(final OutputStream output,
                                      @Nullable final String value) throws IOException {
        if (value == null || value.isBlank()) {
            return;
        }
        final byte[] description = "Source".getBytes(StandardCharsets.UTF_8);
        final byte[] url = value.getBytes(StandardCharsets.UTF_8);
        writeFrameHeader(output, "WXXX", description.length + url.length + 2);
        output.write(3);
        output.write(description);
        output.write(0);
        output.write(url);
    }

    private static void writeFrameHeader(final OutputStream output,
                                         final String id,
                                         final int size) throws IOException {
        output.write(id.getBytes(StandardCharsets.US_ASCII));
        output.write(toSynchsafe(size));
        output.write(0);
        output.write(0);
    }

    private static byte[] toSynchsafe(final int value) {
        return new byte[] {
                (byte) ((value >>> 21) & 0x7F),
                (byte) ((value >>> 14) & 0x7F),
                (byte) ((value >>> 7) & 0x7F),
                (byte) (value & 0x7F)
        };
    }
}
