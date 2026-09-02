package org.schabi.newpipe.player.playback;

import android.content.Context;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.video.PlaceholderSurface;

import org.schabi.newpipe.views.ExpandableSurfaceView;

import java.util.Objects;

/**
 * Prevent error message: 'Unrecoverable player error occurred'
 * In case of rotation some users see this kind of an error which is preventable
 * having a Callback that handles the lifecycle of the surface.
 * <p>
 * How?: In case we are no longer able to write to the surface eg. through rotation/putting in
 * background we set set a DummySurface. Although it it works on API >= 23 only.
 * Result: we get a little video interruption (audio is still fine) but we won't get the
 * 'Unrecoverable player error occurred' error message.
 * <p>
 * This implementation is based on:
 * 'ExoPlayer stuck in buffering after re-adding the surface view a few time #2703'
 * <p>
 * -> exoplayer fix suggestion link
 * https://github.com/google/ExoPlayer/issues/2703#issuecomment-300599981
 */
public final class SurfaceHolderCallback implements SurfaceHolder.Callback {
    private static final double FULLSCREEN_EXPANSION_FACTOR = 1.35;
    private static final double FULLSCREEN_EXIT_SHRINK_FACTOR = 0.80;

    private final Context context;
    private final Player player;
    private PlaceholderSurface placeholderSurface;
    private int previousWidth;
    private int previousHeight;
    private boolean expandedSurfaceSeen;
    private String trackedMediaUri;

    public SurfaceHolderCallback(final Context context, final Player player) {
        this.context = context;
        this.player = player;
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        bindVideoSurface(holder);
    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder,
                               final int format,
                               final int width,
                               final int height) {
        final String currentMediaUri = currentMediaUri();
        if (!Objects.equals(trackedMediaUri, currentMediaUri)) {
            resetSurfaceTransitionTracking();
            trackedMediaUri = currentMediaUri;
        }

        final boolean localMedia = isLocalMediaUri(currentMediaUri);
        if (localMedia && isLargeSurfaceExpansion(
                previousWidth, previousHeight, width, height)) {
            expandedSurfaceSeen = true;
        }

        final boolean recoverSurface = shouldRecoverLocalSurface(
                localMedia,
                expandedSurfaceSeen,
                previousWidth,
                previousHeight,
                width,
                height);
        previousWidth = width;
        previousHeight = height;

        if (recoverSurface) {
            expandedSurfaceSeen = false;
            ExpandableSurfaceView.requestSurfaceRecreation(holder);
        }

        // Some devices keep the same SurfaceView across fullscreen/orientation transitions and
        // only resize its underlying surface. Rebind on every structural surface change so the
        // decoder cannot remain attached to the placeholder/stale fullscreen output.
        bindVideoSurface(holder);
    }

    private String currentMediaUri() {
        final MediaItem mediaItem = player.getCurrentMediaItem();
        if (mediaItem == null || mediaItem.localConfiguration == null) {
            return null;
        }
        return mediaItem.localConfiguration.uri.toString();
    }

    static boolean isLocalMediaUri(final String mediaUri) {
        if (mediaUri == null || mediaUri.isEmpty()) {
            return false;
        }
        final int schemeSeparator = mediaUri.indexOf(':');
        if (schemeSeparator <= 0) {
            return false;
        }
        final String scheme = mediaUri.substring(0, schemeSeparator);
        return "content".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme)
                || "android.resource".equalsIgnoreCase(scheme);
    }

    static boolean isLargeSurfaceExpansion(final int previousWidth,
                                           final int previousHeight,
                                           final int width,
                                           final int height) {
        final long previousArea = surfaceArea(previousWidth, previousHeight);
        final long currentArea = surfaceArea(width, height);
        return previousArea > 0L
                && currentArea > previousArea * FULLSCREEN_EXPANSION_FACTOR;
    }

    static boolean shouldRecoverLocalSurface(final boolean localMedia,
                                             final boolean expandedSurfaceSeen,
                                             final int previousWidth,
                                             final int previousHeight,
                                             final int width,
                                             final int height) {
        if (!localMedia || !expandedSurfaceSeen) {
            return false;
        }
        final long previousArea = surfaceArea(previousWidth, previousHeight);
        final long currentArea = surfaceArea(width, height);
        return previousArea > 0L
                && currentArea > 0L
                && currentArea < previousArea * FULLSCREEN_EXIT_SHRINK_FACTOR;
    }

    private static long surfaceArea(final int width, final int height) {
        return width <= 0 || height <= 0 ? 0L : (long) width * height;
    }

    private void resetSurfaceTransitionTracking() {
        previousWidth = 0;
        previousHeight = 0;
        expandedSurfaceSeen = false;
    }

    private void bindVideoSurface(final SurfaceHolder holder) {
        player.setVideoSurface(holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        if (placeholderSurface == null) {
            placeholderSurface = PlaceholderSurface.newInstanceV17(context, false);
        }
        player.setVideoSurface(placeholderSurface);
    }

    public void release() {
        resetSurfaceTransitionTracking();
        trackedMediaUri = null;
        if (placeholderSurface != null) {
            placeholderSurface.release();
            placeholderSurface = null;
        }
    }
}
