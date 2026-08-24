package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

public final class MaxHeightScrollView extends ScrollView {
    private static final float MAX_SCREEN_HEIGHT_FRACTION = 0.72f;

    public MaxHeightScrollView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int maximumHeight = Math.round(
                getResources().getDisplayMetrics().heightPixels * MAX_SCREEN_HEIGHT_FRACTION);
        final int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        final int cappedHeight = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? maximumHeight : Math.min(maximumHeight, availableHeight);
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(cappedHeight, MeasureSpec.AT_MOST));
    }
}
