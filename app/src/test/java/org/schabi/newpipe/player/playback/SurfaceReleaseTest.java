package org.schabi.newpipe.player.playback;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.video.PlaceholderSurface;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

public class SurfaceReleaseTest {
    @Test
    public void releaseDisconnectsOutputBeforeFreeingPlaceholderAndIgnoresLateCallbacks() {
        final Context context = mock(Context.class);
        final Player player = mock(Player.class);
        final SurfaceHolder holder = mock(SurfaceHolder.class);
        final Surface surface = mock(Surface.class);
        final PlaceholderSurface placeholder = mock(PlaceholderSurface.class);
        when(holder.getSurface()).thenReturn(surface);
        when(surface.isValid()).thenReturn(true);
        try (MockedStatic<PlaceholderSurface> surfaces = mockStatic(PlaceholderSurface.class)) {
            surfaces.when(() -> PlaceholderSurface.newInstanceV17(context, false))
                    .thenReturn(placeholder);
            final SurfaceHolderCallback callback = new SurfaceHolderCallback(context, player);
            callback.surfaceDestroyed(holder);
            callback.release();
            callback.release();
            callback.surfaceCreated(holder);
            callback.surfaceChanged(holder, 0, 90, 160);
            callback.surfaceDestroyed(holder);
            assertFalse(callback.rebindVideoSurfaceIfValid(holder));

            final InOrder order = inOrder(player, placeholder);
            order.verify(player).setVideoSurface(placeholder);
            order.verify(player).clearVideoSurface();
            order.verify(placeholder).release();
            verifyNoMoreInteractions(player, placeholder);
            surfaces.verify(() -> PlaceholderSurface.newInstanceV17(context, false));
        }
    }

    @Test
    public void releaseDisconnectsLiveOutputWithoutCreatingAPlaceholder() {
        final Player player = mock(Player.class);
        final SurfaceHolderCallback callback = new SurfaceHolderCallback(mock(Context.class),
                player);
        callback.release();
        callback.release();
        final InOrder order = inOrder(player);
        order.verify(player).clearVideoSurface();
        verifyNoMoreInteractions(player);
    }
}
