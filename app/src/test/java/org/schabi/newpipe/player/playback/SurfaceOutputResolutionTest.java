package org.schabi.newpipe.player.playback;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.media3.common.C;
import androidx.media3.common.util.Size;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.PlayerMessage;
import androidx.media3.exoplayer.Renderer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 33})
public class SurfaceOutputResolutionTest {
    @Test
    public void initialBindingResizeAndReplayReportOutputSizeToVideoRenderer() {
        final ExoPlayer player = mock(ExoPlayer.class);
        final Renderer video = mock(Renderer.class);
        final Renderer audio = mock(Renderer.class);
        final PlayerMessage message = mock(PlayerMessage.class, RETURNS_SELF);
        final SurfaceHolder holder = mock(SurfaceHolder.class);
        final Surface surface = mock(Surface.class);
        when(holder.getSurface()).thenReturn(surface);
        when(holder.getSurfaceFrame()).thenReturn(new Rect(0, 0, 640, 360));
        when(surface.isValid()).thenReturn(true);
        when(player.getRendererCount()).thenReturn(2);
        when(player.getRendererType(0)).thenReturn(C.TRACK_TYPE_AUDIO);
        when(player.getRendererType(1)).thenReturn(C.TRACK_TYPE_VIDEO);
        when(player.getRenderer(0)).thenReturn(audio);
        when(player.getRenderer(1)).thenReturn(video);
        when(player.createMessage(video)).thenReturn(message);
        final SurfaceHolderCallback callback = new SurfaceHolderCallback(mock(Context.class),
                player);

        callback.surfaceCreated(holder);
        verify(player).setVideoSurface(surface);
        verify(message).setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION);
        verify(message).setPayload(new Size(640, 360));
        verify(message).send();
        verify(player, never()).createMessage(audio);

        clearInvocations(player, message);
        when(holder.getSurfaceFrame()).thenReturn(new Rect(0, 0, 1920, 1080));
        callback.surfaceChanged(holder, 0, 1920, 1080);
        verify(message, org.mockito.Mockito.atLeastOnce()).setPayload(new Size(1920, 1080));
        if (Build.VERSION.SDK_INT >= 33) {
            verify(player, never()).setVideoSurface(surface);
        }

        clearInvocations(player, message);
        assertTrue(callback.rebindVideoSurfaceIfValid(holder));
        verify(message).setPayload(new Size(1920, 1080));
        verify(message).send();

        callback.release();
        clearInvocations(player, message);
        callback.surfaceChanged(holder, 0, 1280, 720);
        org.mockito.Mockito.verifyNoInteractions(player, message);
    }
}
