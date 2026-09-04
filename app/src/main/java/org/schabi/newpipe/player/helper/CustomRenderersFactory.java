package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor;

import java.util.ArrayList;

/**
 * A {@link DefaultRenderersFactory} which only uses {@link CustomMediaCodecVideoRenderer} as an
 * implementation of video codec renders.
 *
 * <p>
 * As no ExoPlayer extension is currently used, the reflection code used by ExoPlayer to try to
 * load video extension libraries is not needed in our case and has been removed. This should be
 * changed in the case an extension is shipped with the app, such as the AV1 one.
 * </p>
 */
public final class CustomRenderersFactory extends DefaultRenderersFactory {
    private final boolean useCustomVideoRenderer;
    private final VisualizerAudioProcessor visualizerAudioProcessor;

    public CustomRenderersFactory(final Context context) {
        this(context, true, new VisualizerAudioProcessor());
    }

    /**
     * Create a renderer factory with decoded-audio visualization support.
     *
     * @param context application context
     * @param useCustomVideoRenderer whether the surface workaround renderer should be used
     * @param visualizerAudioProcessor processor that receives decoded PCM samples
     */
    public CustomRenderersFactory(final Context context,
                                  final boolean useCustomVideoRenderer,
                                  final VisualizerAudioProcessor visualizerAudioProcessor) {
        super(context);
        this.useCustomVideoRenderer = useCustomVideoRenderer;
        this.visualizerAudioProcessor = visualizerAudioProcessor;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    protected void buildVideoRenderers(final Context context,
                                       @ExtensionRendererMode final int extensionRendererMode,
                                       final MediaCodecSelector mediaCodecSelector,
                                       final boolean enableDecoderFallback,
                                       final Handler eventHandler,
                                       final VideoRendererEventListener eventListener,
                                       final long allowedVideoJoiningTimeMs,
                                       final ArrayList<Renderer> out) {
        if (!useCustomVideoRenderer) {
            super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector,
                    enableDecoderFallback, eventHandler, eventListener,
                    allowedVideoJoiningTimeMs, out);
            return;
        }
        out.add(new CustomMediaCodecVideoRenderer(context, getCodecAdapterFactory(),
                mediaCodecSelector, allowedVideoJoiningTimeMs, enableDecoderFallback, eventHandler,
                eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
    }

    @Nullable
    @Override
    protected AudioSink buildAudioSink(final Context context,
                                       final boolean enableFloatOutput,
                                       final boolean enableAudioOutputPlaybackParams) {
        return new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(new AudioProcessor[] {visualizerAudioProcessor})
                .build();
    }
}
