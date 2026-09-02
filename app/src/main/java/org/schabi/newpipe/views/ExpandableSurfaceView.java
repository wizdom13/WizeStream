package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

public class ExpandableSurfaceView extends SurfaceView {
    public static final float MIN_USER_ZOOM = 1.0f;
    public static final float MAX_USER_ZOOM = 4.0f;

    private static final long SURFACE_RECOVERY_SETTLE_DELAY_MILLIS = 120L;
    private static final long SURFACE_RECREATE_GAP_MILLIS = 48L;
    private static final Map<SurfaceHolder, WeakReference<ExpandableSurfaceView>> SURFACE_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private int resizeMode = RESIZE_MODE_FIT;
    private int baseHeight = 0;
    private int maxHeight = 0;
    private float videoAspectRatio = 0.0f;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float userZoomScale = MIN_USER_ZOOM;
    private float userTranslationX;
    private float userTranslationY;
    private int surfaceRecreationGeneration;

    public ExpandableSurfaceView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        SURFACE_VIEWS.put(getHolder(), new WeakReference<>(this));
    }

    /**
     * Requests a one-shot SurfaceView lifecycle restart after the current layout transition
     * settles. Keeping the view invisible for a short frame gap forces Android to tear down the
     * old surface and create a fresh one without changing the player's layout parameters.
     *
     * @param holder holder that belongs to the player surface needing recovery
     * @return whether a matching player SurfaceView was found
     */
    public static boolean requestSurfaceRecreation(@NonNull final SurfaceHolder holder) {
        final WeakReference<ExpandableSurfaceView> reference = SURFACE_VIEWS.get(holder);
        final ExpandableSurfaceView surfaceView = reference == null ? null : reference.get();
        if (surfaceView == null) {
            SURFACE_VIEWS.remove(holder);
            return false;
        }
        surfaceView.scheduleSurfaceRecreation();
        return true;
    }

    private void scheduleSurfaceRecreation() {
        final int generation = ++surfaceRecreationGeneration;
        postDelayed(() -> {
            if (generation != surfaceRecreationGeneration
                    || !isAttachedToWindow()
                    || getVisibility() != View.VISIBLE) {
                return;
            }

            setVisibility(View.INVISIBLE);
            postDelayed(() -> {
                if (generation == surfaceRecreationGeneration
                        && getVisibility() == View.INVISIBLE) {
                    setVisibility(View.VISIBLE);
                }
            }, SURFACE_RECREATE_GAP_MILLIS);
        }, SURFACE_RECOVERY_SETTLE_DELAY_MILLIS);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio == 0.0f) {
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        final boolean verticalVideo = videoAspectRatio < 1;
        // Use maxHeight only on non-fit resize mode and in vertical videos
        int height = maxHeight != 0
                && resizeMode != RESIZE_MODE_FIT
                && verticalVideo ? maxHeight : baseHeight;

        if (width == 0 || height == 0) {
            return;
        }

        final float viewAspectRatio = width / ((float) height);
        final float aspectDeformation = (videoAspectRatio / viewAspectRatio) - 1;
        scaleX = 1.0f;
        scaleY = 1.0f;

        if (resizeMode == RESIZE_MODE_FIT) {
            if (aspectDeformation > 0) {
                height = (int) (width / videoAspectRatio);
            } else {
                width = (int) (height * videoAspectRatio);
            }
        } else if (resizeMode == RESIZE_MODE_ZOOM) {
            if (aspectDeformation < 0) {
                scaleY = viewAspectRatio / videoAspectRatio;
            } else {
                scaleX = videoAspectRatio / viewAspectRatio;
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /**
     * Scale view only in {@link #onLayout} to make transition for ZOOM mode as smooth as possible.
     */
    @Override
    protected void onLayout(final boolean changed,
                            final int left, final int top, final int right, final int bottom) {
        applyUserTransform();
    }

    private void applyUserTransform() {
        final float effectiveScaleX = scaleX * userZoomScale;
        final float effectiveScaleY = scaleY * userZoomScale;
        setPivotX(getWidth() / 2.0f);
        setPivotY(getHeight() / 2.0f);
        setScaleX(effectiveScaleX);
        setScaleY(effectiveScaleY);

        final float maxTranslationX = Math.max(0.0f,
                getWidth() * (effectiveScaleX - 1.0f) / 2.0f);
        final float maxTranslationY = Math.max(0.0f,
                getHeight() * (effectiveScaleY - 1.0f) / 2.0f);
        userTranslationX = Math.max(-maxTranslationX,
                Math.min(userTranslationX, maxTranslationX));
        userTranslationY = Math.max(-maxTranslationY,
                Math.min(userTranslationY, maxTranslationY));
        setTranslationX(userTranslationX);
        setTranslationY(userTranslationY);
    }

    /**
     * @param base The height that will be used in every resize mode as a minimum height
     * @param max  The max height for vertical videos in non-FIT resize modes
     */
    public void setHeights(final int base, final int max) {
        if (baseHeight == base && maxHeight == max) {
            return;
        }
        baseHeight = base;
        maxHeight = max;
        requestLayout();
    }

    public void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int newResizeMode) {
        if (resizeMode == newResizeMode) {
            return;
        }

        resizeMode = newResizeMode;
        requestLayout();
    }

    @AspectRatioFrameLayout.ResizeMode
    public int getResizeMode() {
        return resizeMode;
    }

    public void setUserTransform(final float zoomScale,
                                 final float translationX,
                                 final float translationY) {
        userZoomScale = Math.max(MIN_USER_ZOOM, Math.min(zoomScale, MAX_USER_ZOOM));
        userTranslationX = userZoomScale == MIN_USER_ZOOM ? 0.0f : translationX;
        userTranslationY = userZoomScale == MIN_USER_ZOOM ? 0.0f : translationY;
        applyUserTransform();
    }

    public void resetUserTransform() {
        setUserTransform(MIN_USER_ZOOM, 0.0f, 0.0f);
    }

    public float getUserZoomScale() {
        return userZoomScale;
    }

    public float getUserTranslationX() {
        return userTranslationX;
    }

    public float getUserTranslationY() {
        return userTranslationY;
    }

    public float getVideoAspectRatio() {
        return videoAspectRatio;
    }

    /**
     * Clears geometry left by the previous video while a new video size is not available yet.
     * The surface temporarily fills its parent until {@link #setAspectRatio(float)} receives the
     * new stream dimensions.
     */
    public void clearAspectRatio() {
        videoAspectRatio = 0.0f;
        scaleX = 1.0f;
        scaleY = 1.0f;
        resetUserTransform();
        requestLayout();
    }

    public void setAspectRatio(final float aspectRatio) {
        if (videoAspectRatio == aspectRatio || aspectRatio == 0 || !Float.isFinite(aspectRatio)) {
            return;
        }

        videoAspectRatio = aspectRatio;
        requestLayout();
    }
}
