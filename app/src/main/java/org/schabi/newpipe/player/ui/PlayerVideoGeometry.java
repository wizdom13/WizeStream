package org.schabi.newpipe.player.ui;

import androidx.media3.common.VideoSize;

import org.schabi.newpipe.views.ExpandableSurfaceView;

/** Applies video dimensions, pixel shape, and unapplied rotation to the video surface. */
final class PlayerVideoGeometry {
    private PlayerVideoGeometry() { }

    static void apply(final ExpandableSurfaceView surface, final VideoSize size) {
        final float ratio = calculate(size.width, size.height, size.unappliedRotationDegrees,
                size.pixelWidthHeightRatio);
        if (ratio != 0.0f) {
            surface.setAspectRatio(ratio);
        }
    }

    static float calculate(final int width, final int height, final int rotation,
                           final float pixelRatio) {
        if (width <= 0 || height <= 0) {
            return 0.0f;
        }
        final float safePixelRatio = Float.isFinite(pixelRatio)
                && pixelRatio > 0.0f ? pixelRatio : 1.0f;
        float ratio = width * safePixelRatio / height;
        final int normalizedRotation = Math.floorMod(rotation, 360);
        if (normalizedRotation == 90 || normalizedRotation == 270) {
            ratio = 1.0f / ratio;
        }
        return Float.isFinite(ratio) && ratio > 0.0f ? ratio : 0.0f;
    }
}
