package us.shandian.giga.postprocessing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaLamePcmEncoderTest {
    @Test
    void encodesStereoPcmToMp3Frames() throws Exception {
        final int sampleRate = 44_100;
        final ByteBuffer pcm = ByteBuffer.allocate(sampleRate * 2 * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleRate; i++) {
            final short sample = (short) (Math.sin(2.0d * Math.PI * 440.0d * i / sampleRate)
                    * Short.MAX_VALUE / 2.0d);
            pcm.putShort(sample);
            pcm.putShort(sample);
        }
        pcm.flip();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JavaLamePcmEncoder encoder = new JavaLamePcmEncoder(
                sampleRate, 2, 192, output)) {
            encoder.encode(pcm);
            encoder.finish();
        }

        final byte[] mp3 = output.toByteArray();
        assertTrue(mp3.length > 10_000);
        assertEquals(0xFF, mp3[0] & 0xFF);
        assertEquals(0xE0, mp3[1] & 0xE0);
    }
}
