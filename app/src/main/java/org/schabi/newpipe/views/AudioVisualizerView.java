package org.schabi.newpipe.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor;
import org.schabi.newpipe.player.visualizer.VisualizerStyle;

/** A lightweight collection of visualizers backed by decoded player audio. */
public final class AudioVisualizerView extends View
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int BAR_COUNT = 24;
    private static final float MIN_LEVEL = 0.03f;
    private static final float SMOOTHING = 0.72f;
    private static final int BLUE = Color.rgb(84, 110, 255);
    private static final int PURPLE = Color.rgb(190, 84, 255);
    private static final int CYAN = Color.rgb(84, 255, 218);
    private static final int PINK = Color.rgb(255, 84, 180);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] samples = new float[VisualizerAudioProcessor.SAMPLE_COUNT];
    private final float[] levels = new float[BAR_COUNT];
    private final SharedPreferences preferences;
    private final String stylePreferenceKey;

    @Nullable
    private VisualizerAudioProcessor audioProcessor;
    private VisualizerStyle style = VisualizerStyle.CENTERED_BARS;
    @Nullable
    private Shader verticalGradient;
    @Nullable
    private Shader horizontalGradient;
    private float amplitude;
    private float phase;

    public AudioVisualizerView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        guidePaint.setStyle(Paint.Style.STROKE);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        stylePreferenceKey = context.getString(R.string.visualizer_style_key);
        readStyle();
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(this);
        readStyle();
    }

    @Override
    protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          @Nullable final String key) {
        if (key == null || stylePreferenceKey.equals(key)) {
            readStyle();
            invalidate();
        }
    }

    private void readStyle() {
        style = VisualizerStyle.fromPreferenceValue(preferences.getString(
                stylePreferenceKey, VisualizerStyle.CENTERED_BARS.getPreferenceValue()));
    }

    @Override
    protected void onSizeChanged(final int width, final int height,
                                 final int oldWidth, final int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        verticalGradient = new LinearGradient(0.0f, height, 0.0f, 0.0f,
                new int[]{BLUE, PURPLE, CYAN}, null, Shader.TileMode.CLAMP);
        horizontalGradient = new LinearGradient(0.0f, 0.0f, width, 0.0f,
                new int[]{BLUE, CYAN, PINK}, null, Shader.TileMode.CLAMP);
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
        configurePaints();
        drawStyle(canvas);
        phase = (phase + 0.02f + amplitude * 0.02f) % 1.0f;
        if (getVisibility() == VISIBLE && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    private void configurePaints() {
        paint.setAlpha(255);
        paint.setShader(verticalGradient);
        strokePaint.setAlpha(255);
        strokePaint.setShader(horizontalGradient);
        strokePaint.setStrokeWidth(dp(2.4f));
        guidePaint.setShader(null);
        guidePaint.setColor(Color.WHITE);
        guidePaint.setAlpha(40);
        guidePaint.setStrokeWidth(dp(1.0f));
    }

    private void drawStyle(final Canvas canvas) {
        switch (style) {
            case CLASSIC_BARS:
                drawBars(canvas, BarMode.BOTTOM);
                break;
            case MIRRORED_BARS:
                drawBars(canvas, BarMode.MIRRORED);
                break;
            case WAVEFORM:
                drawWaveform(canvas, false, false);
                break;
            case FILLED_WAVEFORM:
                drawWaveform(canvas, true, false);
                break;
            case OSCILLOSCOPE:
                drawGrid(canvas);
                drawWaveform(canvas, false, true);
                break;
            case SPECTRUM_LINE:
                drawSpectrumPath(canvas, false);
                break;
            case MOUNTAIN:
                drawSpectrumPath(canvas, true);
                break;
            case CIRCULAR_SPECTRUM:
                drawCircularSpectrum(canvas);
                break;
            case RADIAL_WAVEFORM:
                drawRadialWaveform(canvas);
                break;
            case EQUALIZER_BLOCKS:
                drawEqualizerBlocks(canvas);
                break;
            case PARTICLES:
                drawParticles(canvas);
                break;
            case PULSE_RINGS:
                drawPulseRings(canvas);
                break;
            case VU_METERS:
                drawVuMeters(canvas);
                break;
            case NEON_DOTS:
                drawNeonDots(canvas);
                break;
            case CENTERED_BARS:
            default:
                drawBars(canvas, BarMode.CENTERED);
                break;
        }
    }

    private void updateLevels() {
        double squaredSampleSum = 0.0;
        for (final float sample : samples) {
            squaredSampleSum += sample * sample;
        }
        final float currentAmplitude = (float) Math.min(1.0,
                Math.sqrt(squaredSampleSum / samples.length) * 3.2);
        amplitude = Math.max(currentAmplitude, amplitude * 0.84f);

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

    private void drawBars(final Canvas canvas, final BarMode mode) {
        final float slotWidth = getWidth() / (float) BAR_COUNT;
        final float barWidth = slotWidth * 0.6f;
        final float centerY = getHeight() / 2.0f;
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final float centerX = slotWidth * (bar + 0.5f);
            final float height = getHeight() * level(bar);
            if (mode == BarMode.BOTTOM) {
                drawRoundedBar(canvas, centerX, getHeight() - height, getHeight(), barWidth);
            } else if (mode == BarMode.MIRRORED) {
                final float gap = dp(3.0f);
                drawRoundedBar(canvas, centerX, centerY - gap - height * 0.45f,
                        centerY - gap, barWidth);
                drawRoundedBar(canvas, centerX, centerY + gap,
                        centerY + gap + height * 0.45f, barWidth);
            } else {
                drawRoundedBar(canvas, centerX, centerY - height / 2.0f,
                        centerY + height / 2.0f, barWidth);
            }
        }
    }

    private void drawRoundedBar(final Canvas canvas, final float centerX,
                                final float top, final float bottom, final float width) {
        canvas.drawRoundRect(centerX - width / 2.0f, top,
                centerX + width / 2.0f, bottom,
                width / 2.0f, width / 2.0f, paint);
    }

    private void drawWaveform(final Canvas canvas, final boolean filled,
                              final boolean compact) {
        final float centerY = getHeight() / 2.0f;
        final float scale = getHeight() * (compact ? 0.28f : 0.43f);
        path.reset();
        path.moveTo(0.0f, centerY);
        for (int sample = 0; sample < samples.length; sample++) {
            final float x = sample * getWidth() / (float) (samples.length - 1);
            path.lineTo(x, centerY - normalizedSample(sample) * scale);
        }
        if (filled) {
            path.lineTo(getWidth(), centerY);
            path.close();
            paint.setAlpha(200);
            canvas.drawPath(path, paint);
            paint.setAlpha(255);
        } else {
            canvas.drawPath(path, strokePaint);
        }
    }

    private void drawGrid(final Canvas canvas) {
        for (int line = 1; line < 4; line++) {
            final float y = line * getHeight() / 4.0f;
            canvas.drawLine(0.0f, y, getWidth(), y, guidePaint);
        }
        for (int line = 1; line < 8; line++) {
            final float x = line * getWidth() / 8.0f;
            canvas.drawLine(x, 0.0f, x, getHeight(), guidePaint);
        }
    }

    private void drawSpectrumPath(final Canvas canvas, final boolean filled) {
        path.reset();
        if (filled) {
            path.moveTo(0.0f, getHeight());
        }
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final float x = bar * getWidth() / (float) (BAR_COUNT - 1);
            final float y = getHeight() * (0.94f - level(bar) * 0.88f);
            if (bar == 0 && !filled) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        if (filled) {
            path.lineTo(getWidth(), getHeight());
            path.close();
            paint.setAlpha(210);
            canvas.drawPath(path, paint);
            paint.setAlpha(255);
        } else {
            strokePaint.setStrokeWidth(dp(3.0f));
            canvas.drawPath(path, strokePaint);
        }
    }

    private void drawCircularSpectrum(final Canvas canvas) {
        final float centerX = getWidth() / 2.0f;
        final float centerY = getHeight() / 2.0f;
        final float baseRadius = Math.min(getWidth(), getHeight()) * 0.22f;
        final float range = Math.min(getWidth(), getHeight()) * 0.24f;
        strokePaint.setStrokeWidth(dp(4.0f));
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final double angle = Math.PI * 2.0 * bar / BAR_COUNT - Math.PI / 2.0;
            final float outerRadius = baseRadius + range * level(bar);
            canvas.drawLine(centerX + (float) Math.cos(angle) * baseRadius,
                    centerY + (float) Math.sin(angle) * baseRadius,
                    centerX + (float) Math.cos(angle) * outerRadius,
                    centerY + (float) Math.sin(angle) * outerRadius,
                    strokePaint);
        }
    }

    private void drawRadialWaveform(final Canvas canvas) {
        final float centerX = getWidth() / 2.0f;
        final float centerY = getHeight() / 2.0f;
        final float baseRadius = Math.min(getWidth(), getHeight()) * 0.28f;
        final float scale = Math.min(getWidth(), getHeight()) * 0.14f;
        path.reset();
        for (int sample = 0; sample <= samples.length; sample++) {
            final int index = sample % samples.length;
            final double angle = Math.PI * 2.0 * sample / samples.length - Math.PI / 2.0;
            final float radius = baseRadius + normalizedSample(index) * scale;
            final float x = centerX + (float) Math.cos(angle) * radius;
            final float y = centerY + (float) Math.sin(angle) * radius;
            if (sample == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        canvas.drawPath(path, strokePaint);
    }

    private void drawEqualizerBlocks(final Canvas canvas) {
        final int blockCount = 12;
        final float slotWidth = getWidth() / (float) BAR_COUNT;
        final float blockWidth = slotWidth * 0.64f;
        final float blockHeight = getHeight() / (float) blockCount;
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final int activeBlocks = Math.max(1, Math.round(level(bar) * blockCount));
            final float left = slotWidth * bar + (slotWidth - blockWidth) / 2.0f;
            for (int block = 0; block < activeBlocks; block++) {
                final float bottom = getHeight() - block * blockHeight;
                canvas.drawRoundRect(left, bottom - blockHeight * 0.82f,
                        left + blockWidth, bottom - blockHeight * 0.16f,
                        dp(1.5f), dp(1.5f), paint);
            }
        }
    }

    private void drawParticles(final Canvas canvas) {
        paint.setShader(horizontalGradient);
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final float energy = level(bar);
            final int count = 1 + Math.round(energy * 4.0f);
            for (int particle = 0; particle < count; particle++) {
                final float progress = (phase + bar * 0.071f + particle * 0.19f) % 1.0f;
                final float x = (bar + 0.5f) * getWidth() / BAR_COUNT
                        + (float) Math.sin((bar + particle) * 2.3f) * dp(5.0f);
                final float radius = dp(1.8f + energy * 3.2f) * (1.0f - progress * 0.45f);
                paint.setAlpha(Math.max(45, Math.round(255.0f * (1.0f - progress))));
                canvas.drawCircle(x, getHeight() * (1.0f - progress), radius, paint);
            }
        }
        paint.setAlpha(255);
    }

    private void drawPulseRings(final Canvas canvas) {
        final float centerX = getWidth() / 2.0f;
        final float centerY = getHeight() / 2.0f;
        final float maximumRadius = Math.min(getWidth(), getHeight()) * 0.46f;
        for (int ring = 0; ring < 5; ring++) {
            final float progress = (phase + ring / 5.0f) % 1.0f;
            strokePaint.setAlpha(Math.max(35, Math.round(255.0f * (1.0f - progress))));
            strokePaint.setStrokeWidth(dp(2.0f + amplitude * 5.0f));
            canvas.drawCircle(centerX, centerY,
                    maximumRadius * (0.16f + progress * 0.78f), strokePaint);
        }
        strokePaint.setAlpha(255);
    }

    private void drawVuMeters(final Canvas canvas) {
        final float margin = getWidth() * 0.08f;
        final float width = getWidth() - margin * 2.0f;
        final float height = Math.max(dp(18.0f), getHeight() * 0.14f);
        drawMeter(canvas, margin, getHeight() * 0.28f, width, height,
                averageLevel(0, BAR_COUNT / 2));
        drawMeter(canvas, margin, getHeight() * 0.58f, width, height,
                averageLevel(BAR_COUNT / 2, BAR_COUNT));
    }

    private void drawMeter(final Canvas canvas, final float left, final float top,
                           final float width, final float height, final float energy) {
        guidePaint.setStyle(Paint.Style.FILL);
        guidePaint.setAlpha(28);
        canvas.drawRoundRect(left, top, left + width, top + height,
                height / 2.0f, height / 2.0f, guidePaint);
        paint.setShader(horizontalGradient);
        canvas.drawRoundRect(left, top, left + width * energy, top + height,
                height / 2.0f, height / 2.0f, paint);
        guidePaint.setStyle(Paint.Style.STROKE);
    }

    private void drawNeonDots(final Canvas canvas) {
        final float slotWidth = getWidth() / (float) BAR_COUNT;
        final float centerY = getHeight() / 2.0f;
        paint.setShader(horizontalGradient);
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            final float x = slotWidth * (bar + 0.5f);
            final float offset = getHeight() * level(bar) * 0.44f;
            final float radius = Math.max(dp(2.2f), slotWidth * 0.18f);
            paint.setAlpha(70);
            canvas.drawCircle(x, centerY - offset, radius * 2.3f, paint);
            canvas.drawCircle(x, centerY + offset, radius * 2.3f, paint);
            paint.setAlpha(255);
            canvas.drawCircle(x, centerY - offset, radius, paint);
            canvas.drawCircle(x, centerY + offset, radius, paint);
        }
    }

    private float averageLevel(final int start, final int end) {
        float sum = 0.0f;
        for (int index = start; index < end; index++) {
            sum += level(index);
        }
        return Math.min(1.0f, sum / Math.max(1, end - start) * 2.2f);
    }

    private float level(final int bar) {
        return Math.max(MIN_LEVEL, levels[bar]);
    }

    private float normalizedSample(final int sample) {
        return Math.max(-1.0f, Math.min(1.0f, samples[sample] * 2.8f));
    }

    private float dp(final float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private enum BarMode {
        CENTERED,
        BOTTOM,
        MIRRORED
    }
}
