package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

/** Clips thumbnail overlays to the same rounded outline as the thumbnail image. */
public final class RoundedThumbnailContainer extends FrameLayout {
    private final Path clipPath = new Path();
    private final RectF clipBounds = new RectF();
    private final float cornerRadius;

    public RoundedThumbnailContainer(@NonNull final Context context) {
        this(context, null);
    }

    public RoundedThumbnailContainer(@NonNull final Context context,
                                     @Nullable final AttributeSet attrs) {
        super(context, attrs);
        cornerRadius = getResources().getDimension(R.dimen.stream_thumbnail_corner_radius);
        setClipChildren(true);
    }

    @Override
    protected void onSizeChanged(final int width, final int height,
                                 final int oldWidth, final int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        clipBounds.set(0.0f, 0.0f, width, height);
        clipPath.reset();
        clipPath.addRoundRect(clipBounds, cornerRadius, cornerRadius, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(@NonNull final Canvas canvas) {
        final int checkpoint = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }
}
