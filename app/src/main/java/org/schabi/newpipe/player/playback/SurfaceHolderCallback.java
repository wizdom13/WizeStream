package org.schabi.newpipe.player.playback;

import android.content.Context;
import android.os.Build;
import android.view.SurfaceHolder;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.video.PlaceholderSurface;

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

    private final Context context;
    private final Player player;
    private PlaceholderSurface placeholderSurface;

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
        // Before Android 14 the SurfaceView can retain its holder while fullscreen/orientation
        // changes leave the decoder attached to a stale output. Android 14+ uses
        // SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT in ExpandableSurfaceView, so surfaceCreated already
        // owns the live output and rebinding it for a size-only callback is redundant. Avoiding
        // that extra setVideoSurface call also prevents Media3's synchronous detach timeout.
        if (shouldRebindOnSurfaceChanged(Build.VERSION.SDK_INT)) {
            bindVideoSurface(holder);
        }
    }

    static boolean shouldRebindOnSurfaceChanged(final int sdkInt) {
        return sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    private void bindVideoSurface(final SurfaceHolder holder) {
        player.setVideoSurface(holder.getSurface());
    }

    /**
     * Rebinds the current surface after the display wakes if Android retained the view without
     * dispatching a fresh surface callback.
     *
     * @param holder holder belonging to the visible player surface
     * @return whether a valid surface was rebound
     */
    public boolean rebindVideoSurfaceIfValid(final SurfaceHolder holder) {
        if (!holder.getSurface().isValid()) {
            return false;
        }
        bindVideoSurface(holder);
        return true;
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        if (placeholderSurface == null) {
            placeholderSurface = PlaceholderSurface.newInstanceV17(context, false);
        }
        player.setVideoSurface(placeholderSurface);
    }

    public void release() {
        if (placeholderSurface != null) {
            placeholderSurface.release();
            placeholderSurface = null;
        }
    }
}
