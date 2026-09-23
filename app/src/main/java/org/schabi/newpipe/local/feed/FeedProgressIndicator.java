package org.schabi.newpipe.local.feed;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Compact feed progress indicator that preserves the base ProgressBar contract while drawing a
 * Material-colored wave for both determinate and indeterminate refresh states.
 */
public final class FeedProgressIndicator extends ProgressBar {
    private static final long INDETERMINATE_DURATION_MILLIS = 1_100L;
    private static final long PROGRESS_ANIMATION_DURATION_MILLIS = 180L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();

    private final float waveAmplitude;
    private final float waveLength;
    private final float strokeWidth;
    private final float indeterminateSegmentWidth;

    @Nullable
    private ValueAnimator indeterminateAnimator;
    @Nullable
    private ValueAnimator progressAnimator;

    private float displayedProgress;
    private float phase;

    public FeedProgressIndicator(@NonNull final Context context) {
        this(context, null);
    }

    public FeedProgressIndicator(@NonNull final Context context,
                                 @Nullable final AttributeSet attrs) {
        super(context, attrs);

        final float density = getResources().getDisplayMetrics().density;
        waveAmplitude = 3.0f * density;
        waveLength = 16.0f * density;
        strokeWidth = 4.0f * density;
        indeterminateSegmentWidth = 72.0f * density;

        configurePaint(trackPaint, MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurfaceVariant));
        configurePaint(indicatorPaint, MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorPrimary));

        super.setIndeterminate(false);
        super.setMax(1);
        super.setProgress(0);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private void configurePaint(@NonNull final Paint paint, final int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    @Override
    public synchronized void setMax(final int value) {
        super.setMax(Math.max(1, value));
        displayedProgress = Math.min(displayedProgress, getMax());
        invalidate();
    }

    @Override
    public synchronized void setProgress(final int value) {
        super.setProgress(value);
        displayedProgress = getProgress();
        invalidate();
    }

    public void setProgressCompat(final int value, final boolean animated) {
        final int target = Math.max(0, Math.min(value, getMax()));
        super.setProgress(target);

        if (!animated || isIndeterminate() || !isAttachedToWindow()) {
            cancelProgressAnimator();
            displayedProgress = target;
            invalidate();
            return;
        }

        cancelProgressAnimator();
        progressAnimator = ValueAnimator.ofFloat(displayedProgress, target);
        progressAnimator.setDuration(PROGRESS_ANIMATION_DURATION_MILLIS);
        progressAnimator.addUpdateListener(animation -> {
            displayedProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    @Override
    public synchronized void setIndeterminate(final boolean value) {
        if (isIndeterminate() == value) {
            return;
        }

        super.setIndeterminate(value);
        if (value) {
            cancelProgressAnimator();
            startIndeterminateAnimator();
        } else {
            cancelIndeterminateAnimator();
            phase = 0.0f;
            displayedProgress = getProgress();
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isIndeterminate()) {
            startIndeterminateAnimator();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelIndeterminateAnimator();
        cancelProgressAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateIndeterminateAnimatorState();
    }

    @Override
    protected void onWindowVisibilityChanged(final int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateIndeterminateAnimatorState();
    }

    private void updateIndeterminateAnimatorState() {
        if (isIndeterminate() && isShown() && getWindowVisibility() == VISIBLE) {
            startIndeterminateAnimator();
        } else {
            cancelIndeterminateAnimator();
        }
    }

    private void startIndeterminateAnimator() {
        if (!isAttachedToWindow()
                || !isShown()
                || getWindowVisibility() != VISIBLE
                || indeterminateAnimator != null) {
            return;
        }

        indeterminateAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        indeterminateAnimator.setDuration(INDETERMINATE_DURATION_MILLIS);
        indeterminateAnimator.setRepeatCount(ValueAnimator.INFINITE);
        indeterminateAnimator.setRepeatMode(ValueAnimator.RESTART);
        indeterminateAnimator.setInterpolator(new LinearInterpolator());
        indeterminateAnimator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        indeterminateAnimator.start();
    }

    private void cancelIndeterminateAnimator() {
        if (indeterminateAnimator != null) {
            indeterminateAnimator.cancel();
            indeterminateAnimator = null;
        }
    }

    private void cancelProgressAnimator() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
    }

    @Override
    protected synchronized void onDraw(@NonNull final Canvas canvas) {
        final float left = getPaddingLeft();
        final float right = getWidth() - getPaddingRight();
        if (right <= left) {
            return;
        }

        buildWavePath(left, right);
        canvas.drawPath(wavePath, trackPaint);

        final int saveCount = canvas.save();
        if (isIndeterminate()) {
            clipIndeterminateSegment(canvas, left, right);
        } else {
            clipDeterminateSegment(canvas, left, right);
        }
        canvas.drawPath(wavePath, indicatorPaint);
        canvas.restoreToCount(saveCount);
    }

    private void buildWavePath(final float left, final float right) {
        wavePath.reset();

        final float centerY = (getPaddingTop() + getHeight() - getPaddingBottom()) / 2.0f;
        final float phaseOffset = isIndeterminate() ? phase * TWO_PI : 0.0f;
        final float step = Math.max(1.0f, getResources().getDisplayMetrics().density);

        wavePath.moveTo(left, centerY);
        for (float x = left; x <= right; x += step) {
            final float normalized = (x - left) / waveLength;
            final float y = centerY + waveAmplitude
                    * (float) Math.sin(normalized * TWO_PI + phaseOffset);
            wavePath.lineTo(x, y);
        }
        wavePath.lineTo(right, centerY + waveAmplitude
                * (float) Math.sin(((right - left) / waveLength) * TWO_PI + phaseOffset));
    }

    private void clipDeterminateSegment(@NonNull final Canvas canvas,
                                        final float left,
                                        final float right) {
        final float fraction = getMax() <= 0 ? 0.0f : displayedProgress / getMax();
        final float clampedFraction = Math.max(0.0f, Math.min(1.0f, fraction));

        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            final float start = right - (right - left) * clampedFraction;
            canvas.clipRect(start, 0.0f, right, getHeight());
        } else {
            final float end = left + (right - left) * clampedFraction;
            canvas.clipRect(left, 0.0f, end, getHeight());
        }
    }

    private void clipIndeterminateSegment(@NonNull final Canvas canvas,
                                          final float left,
                                          final float right) {
        final float availableWidth = right - left;
        final float travel = availableWidth + indeterminateSegmentWidth * 2.0f;
        final float directionPhase = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? 1.0f - phase
                : phase;
        final float segmentStart = left - indeterminateSegmentWidth
                + travel * directionPhase;
        final float segmentEnd = segmentStart + indeterminateSegmentWidth;

        canvas.clipRect(
                Math.max(left, segmentStart),
                0.0f,
                Math.min(right, segmentEnd),
                getHeight()
        );
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ProgressBar.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull final AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(ProgressBar.class.getName());
        if (!isIndeterminate()) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                    0.0f,
                    getMax(),
                    getProgress()
            ));
        }
    }
}
