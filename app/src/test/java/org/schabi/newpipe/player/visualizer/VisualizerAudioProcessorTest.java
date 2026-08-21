package org.schabi.newpipe.player.visualizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VisualizerAudioProcessorTest {
    @Test
    public void processorAcceptsSharedEmptyInput() throws Exception {
        final VisualizerAudioProcessor processor = new VisualizerAudioProcessor();
        processor.configure(new AudioProcessor.AudioFormat(48_000, 2,
                C.ENCODING_PCM_16BIT));
        processor.flush();

        processor.queueInput(AudioProcessor.EMPTY_BUFFER);

        assertEquals(0, processor.getOutput().remaining());
    }

    @Test
    public void processorPassesAudioThroughAndCapturesWaveform() throws Exception {
        final VisualizerAudioProcessor processor = new VisualizerAudioProcessor();
        processor.setEnabled(true);
        processor.configure(new AudioProcessor.AudioFormat(48_000, 2,
                C.ENCODING_PCM_16BIT));
        processor.flush();

        final ByteBuffer input = ByteBuffer.allocateDirect(512)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 128; i++) {
            input.putShort((short) 16_384);
            input.putShort((short) 16_384);
        }
        input.flip();
        processor.queueInput(input);

        assertEquals(512, processor.getOutput().remaining());
        final float[] waveform = new float[VisualizerAudioProcessor.SAMPLE_COUNT];
        assertEquals(waveform.length, processor.copyLatestSamples(waveform));
        assertTrue(waveform[0] > 0.49f);
    }
}
