package org.schabi.newpipe.player.visualizer;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pass-through PCM processor that exposes a small, normalized waveform for the player visualizer.
 * It operates on decoded audio and therefore does not require microphone access.
 */
public final class VisualizerAudioProcessor extends BaseAudioProcessor {
    public static final int SAMPLE_COUNT = 128;

    private final float[] workingSamples = new float[SAMPLE_COUNT];
    private volatile float[] latestSamples = new float[SAMPLE_COUNT];
    private volatile boolean enabled;

    /**
     * Enable or disable waveform capture. Audio remains pass-through in both states.
     *
     * @param enabled whether waveform capture should run
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            latestSamples = new float[SAMPLE_COUNT];
        }
    }

    /**
     * Copy the latest waveform into a caller-owned array.
     *
     * @param target destination array
     * @return number of samples copied
     */
    public int copyLatestSamples(final float[] target) {
        final float[] snapshot = latestSamples;
        final int count = Math.min(target.length, snapshot.length);
        System.arraycopy(snapshot, 0, target, 0, count);
        return count;
    }

    @Override
    protected AudioFormat onConfigure(final AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        return inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                ? inputAudioFormat : AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(final ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return;
        }

        final int size = inputBuffer.remaining();
        if (enabled && size >= 2) {
            captureWaveform(inputBuffer);
        }
        final ByteBuffer outputBuffer = replaceOutputBuffer(size);
        outputBuffer.put(inputBuffer);
        outputBuffer.flip();
    }

    private void captureWaveform(final ByteBuffer inputBuffer) {
        final ByteBuffer samples = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        final int channelCount = Math.max(1, inputAudioFormat.channelCount);
        final int frameSize = channelCount * 2;
        final int frameCount = samples.remaining() / frameSize;
        if (frameCount == 0) {
            return;
        }

        Arrays.fill(workingSamples, 0.0f);
        for (int outputIndex = 0; outputIndex < SAMPLE_COUNT; outputIndex++) {
            final int frame = Math.min(frameCount - 1,
                    outputIndex * frameCount / SAMPLE_COUNT);
            final int frameOffset = samples.position() + frame * frameSize;
            float mixed = 0.0f;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += samples.getShort(frameOffset + channel * 2) / 32768.0f;
            }
            workingSamples[outputIndex] = mixed / channelCount;
        }
        latestSamples = workingSamples.clone();
    }
}
