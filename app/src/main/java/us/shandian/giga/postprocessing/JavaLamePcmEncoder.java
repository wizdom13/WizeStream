package us.shandian.giga.postprocessing;

import co.ntbl.lame.mp3.Lame;
import co.ntbl.lame.mp3.MPEGMode;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class JavaLamePcmEncoder implements Closeable {
    private static final int QUALITY = 5;
    private static final int SAMPLES_PER_CHUNK = 8_192;

    private final int channels;
    private final Lame lame;
    private final OutputStream output;
    private final byte[] encoded = new byte[SAMPLES_PER_CHUNK * 5 / 4 + 7_200];

    JavaLamePcmEncoder(final int sampleRate,
                       final int channels,
                       final int bitrateKbps,
                       final OutputStream output) {
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("MP3 output supports mono or stereo PCM");
        }
        this.channels = channels;
        this.output = output;
        this.lame = new Lame();
        lame.getFlags().setInNumChannels(channels);
        lame.getFlags().setInSampleRate(sampleRate);
        if (sampleRate < 32_000) {
            // MPEG-1 supports the complete 128-320 kbps range; LAME performs the resampling.
            lame.getFlags().setOutSampleRate(32_000);
        }
        lame.getFlags().setMode(channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO);
        lame.getFlags().setBitRate(bitrateKbps);
        lame.getFlags().setQuality(QUALITY);
        lame.getFlags().setFindReplayGain(true);
        lame.getFlags().setWriteId3tagAutomatic(false);
        lame.getId3().init(lame.getFlags());
        final int result = lame.initParams();
        if (result < 0) {
            lame.close();
            throw new IllegalArgumentException("Unsupported LAME encoder parameters: " + result);
        }
    }

    void encode(final ByteBuffer pcm) throws IOException {
        pcm.order(ByteOrder.LITTLE_ENDIAN);
        final int frameCount = pcm.remaining() / (Short.BYTES * channels);
        int framesRemaining = frameCount;
        while (framesRemaining > 0) {
            final int frames = Math.min(framesRemaining, SAMPLES_PER_CHUNK);
            final float[] left = new float[frames];
            final float[] right = new float[frames];
            for (int i = 0; i < frames; i++) {
                left[i] = pcm.getShort() * 65_536.0f;
                right[i] = channels == 2 ? pcm.getShort() * 65_536.0f : left[i];
            }
            final int written = lame.encodeBuffer(left, right, frames, encoded);
            if (written < 0) {
                throw new IOException("LAME encoding failed: " + written);
            }
            output.write(encoded, 0, written);
            framesRemaining -= frames;
        }
    }

    void finish() throws IOException {
        final int written = lame.encodeFlush(encoded);
        if (written < 0) {
            throw new IOException("LAME flush failed: " + written);
        }
        output.write(encoded, 0, written);
        output.flush();
    }

    @Override
    public void close() {
        lame.close();
    }
}
