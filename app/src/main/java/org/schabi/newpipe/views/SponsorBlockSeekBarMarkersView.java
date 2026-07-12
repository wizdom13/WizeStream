package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SponsorBlockSeekBarMarkersView extends View {
    private static final int MIN_MARKER_WIDTH_PX = 2;

    @NonNull
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private List<SponsorBlockSegment> segments = Collections.emptyList();
    private long durationMillis = 0;

    public SponsorBlockSeekBarMarkersView(final Context context) {
        super(context);
        init();
    }

    public SponsorBlockSeekBarMarkersView(final Context context,
                                          @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SponsorBlockSeekBarMarkersView(final Context context,
                                          @Nullable final AttributeSet attrs,
                                          final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setEnabled(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setSegments(@NonNull final List<SponsorBlockSegment> newSegments,
                            final long newDurationMillis) {
        if (newSegments.isEmpty() || newDurationMillis <= 0) {
            clearSegments();
            return;
        }

        segments = new ArrayList<>(newSegments);
        durationMillis = newDurationMillis;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void clearSegments() {
        if (segments.isEmpty() && getVisibility() == GONE) {
            return;
        }

        segments = Collections.emptyList();
        durationMillis = 0;
        setVisibility(GONE);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        if (segments.isEmpty() || durationMillis <= 0) {
            return;
        }

        final int width = getWidth();
        final int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        final int leftBound = clamp(getPaddingLeft(), 0, width);
        final int rightBound = clamp(width - getPaddingRight(), leftBound, width);
        final int availableWidth = rightBound - leftBound;
        if (availableWidth <= 0) {
            return;
        }

        final float configuredMarkerHeight = getResources()
                .getDimension(R.dimen.sponsor_block_marker_height);
        final float markerHeight = Math.min(height, Math.max(1.0f, configuredMarkerHeight));
        final float top = (height - markerHeight) / 2.0f;
        final float bottom = top + markerHeight;

        for (final SponsorBlockSegment segment : segments) {
            final long startMillis = Math.round(segment.startTime);
            final long endMillis = Math.round(segment.endTime);
            if (startMillis < 0 || startMillis >= durationMillis) {
                continue;
            }

            final float startFraction = Math.max(0.0f,
                    Math.min(1.0f, startMillis / (float) durationMillis));
            final float left = clamp(leftBound + (availableWidth * startFraction), 0.0f, width);
            final float right;
            if (segment.action == SponsorBlockAction.POI || endMillis <= startMillis) {
                right = clamp(left + MIN_MARKER_WIDTH_PX, left, rightBound);
            } else {
                final float endFraction = Math.max(startFraction,
                        Math.min(1.0f, endMillis / (float) durationMillis));
                final float unclampedRight = Math.max(left + MIN_MARKER_WIDTH_PX,
                        leftBound + (availableWidth * endFraction));
                right = clamp(unclampedRight, left, rightBound);
            }
            if (right <= left) {
                continue;
            }

            paint.setColor(getSegmentColor(segment.category));
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(max, value));
    }

    @ColorInt
    private int getSegmentColor(@Nullable final SponsorBlockCategory category) {
        return SponsorBlockCategoryRepository.getColor(getContext(), category);
    }
}
