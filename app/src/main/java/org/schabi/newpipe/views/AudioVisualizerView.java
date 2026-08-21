package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor;

/** A lightweight spectrum view backed by decoded player audio. */
public final class AudioVisualizerView extends View {
    private static final int BAR_COUNT = 24;
    private static final float MIN_BAR_HEIGHT = 0.03f;
    private static final float SMOOTHING = 0.72f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] samples = new float[VisualizerAudioProcessor.SAMPLE_COUNT];
    private final float[] levels = new float[BAR_COUNT];
    @Nullable
    private VisualizerAudioProcessor audioProcessor;

    public AudioVisualizerView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * Set the decoded-audio source used by this view.
     *
     * @param processor visualizer processor, or {@code null} to detach
     */
    public void setAudioProcessor(@Nullable final VisualizerAudioProcessor processor) {
        audioProcessor = processor;
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final VisualizerAudioProcessor processor = audioProcessor;
        if (processor == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        processor.copyLatestSamples(samples);
        updateLevels();
        drawBars(canvas);
        if (getVisibility() == VISIBLE && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    private void updateLevels() {
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final int frequencyBin = bar + 1;
            double real = 0.0;
            double imaginary = 0.0;
            for (int sample = 0; sample < samples.length; sample++) {
                final double window = 0.5 - 0.5
                        * Math.cos(2.0 * Math.PI * sample / (samples.length - 1));
                final double angle = 2.0 * Math.PI * frequencyBin * sample / samples.length;
                real += samples[sample] * window * Math.cos(angle);
                imaginary -= samples[sample] * window * Math.sin(angle);
            }
            final float magnitude = (float) Math.min(1.0,
                    Math.hypot(real, imaginary) * 4.0 / samples.length);
            levels[bar] = Math.max(magnitude, levels[bar] * SMOOTHING);
        }
    }

    private void drawBars(final Canvas canvas) {
        paint.setShader(new LinearGradient(0.0f, getHeight(), 0.0f, 0.0f,
                Color.rgb(84, 110, 255), Color.rgb(84, 255, 218),
                Shader.TileMode.CLAMP));
        final float slotWidth = getWidth() / (float) BAR_COUNT;
        final float barWidth = slotWidth * 0.58f;
        final float cornerRadius = barWidth / 2.0f;
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final float centerX = slotWidth * (bar + 0.5f);
            final float height = getHeight()
                    * Math.max(MIN_BAR_HEIGHT, levels[bar]);
            final float top = (getHeight() - height) / 2.0f;
            final float bottom = (getHeight() + height) / 2.0f;
            canvas.drawRoundRect(centerX - barWidth / 2.0f, top,
                    centerX + barWidth / 2.0f, bottom, cornerRadius, cornerRadius, paint);
        }
    }
}
