package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {28, 35})
public class SurfaceRecoveryRendererFactoryTest {
    @Test
    public void recoveryEnablesSafeSurfaceSwitchingForReplacementEngines() {
        final var context = RuntimeEnvironment.getApplication();
        final var factory = new CustomRenderersFactory(
                context, false, new VisualizerAudioProcessor());
        assertFalse(videoRenderer(factory) instanceof CustomMediaCodecVideoRenderer);
        factory.enableSurfaceWorkaround();
        assertTrue(videoRenderer(factory) instanceof CustomMediaCodecVideoRenderer);
        factory.enableSurfaceWorkaround();
        assertTrue(videoRenderer(factory) instanceof CustomMediaCodecVideoRenderer);
    }

    private Renderer videoRenderer(final CustomRenderersFactory factory) {
        final var renderers = new ArrayList<Renderer>();
        factory.buildVideoRenderers(RuntimeEnvironment.getApplication(),
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, MediaCodecSelector.DEFAULT,
                false, new Handler(Looper.getMainLooper()), new VideoRendererEventListener() { },
                5000, renderers);
        return renderers.get(0);
    }
}
