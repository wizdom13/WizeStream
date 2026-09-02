package org.schabi.newpipe.player.playback;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.Player;

import org.junit.Test;

public class SurfaceHolderCallbackTest {
    @Test
    public void surfaceChangedRebindsCurrentVideoSurface() {
        final Context context = mock(Context.class);
        final Player player = mock(Player.class);
        final SurfaceHolder holder = mock(SurfaceHolder.class);
        final Surface surface = mock(Surface.class);
        when(holder.getSurface()).thenReturn(surface);

        final SurfaceHolderCallback callback = new SurfaceHolderCallback(context, player);
        callback.surfaceChanged(holder, 0, 1920, 1080);

        verify(player).setVideoSurface(surface);
    }

    @Test
    public void surfaceCreatedBindsCurrentVideoSurface() {
        final Context context = mock(Context.class);
        final Player player = mock(Player.class);
        final SurfaceHolder holder = mock(SurfaceHolder.class);
        final Surface surface = mock(Surface.class);
        when(holder.getSurface()).thenReturn(surface);

        final SurfaceHolderCallback callback = new SurfaceHolderCallback(context, player);
        callback.surfaceCreated(holder);

        verify(player).setVideoSurface(surface);
    }
}
