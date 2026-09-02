package org.schabi.newpipe.player.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.Player;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SurfaceHolderCallbackTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

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

    @Test
    public void localMediaUriDetectionRejectsRemoteStreams() {
        assertTrue(SurfaceHolderCallback.isLocalMediaUri("content://media/video/42"));
        assertTrue(SurfaceHolderCallback.isLocalMediaUri("file:///storage/emulated/0/video.mp4"));
        assertTrue(SurfaceHolderCallback.isLocalMediaUri("android.resource://app/raw/video"));
        assertFalse(SurfaceHolderCallback.isLocalMediaUri("https://example.com/video.mp4"));
        assertFalse(SurfaceHolderCallback.isLocalMediaUri(null));
    }

    @Test
    public void fullscreenSizedExpansionThenShrinkQualifiesForLocalRecovery() {
        assertTrue(SurfaceHolderCallback.isLargeSurfaceExpansion(
                1080, 607, 2400, 1080));
        assertTrue(SurfaceHolderCallback.shouldRecoverLocalSurface(
                true, true, 2400, 1080, 1080, 607));
    }

    @Test
    public void recoveryDoesNotRunForRemoteOrOrdinaryResizes() {
        assertFalse(SurfaceHolderCallback.shouldRecoverLocalSurface(
                false, true, 2400, 1080, 1080, 607));
        assertFalse(SurfaceHolderCallback.shouldRecoverLocalSurface(
                true, false, 2400, 1080, 1080, 607));
        assertFalse(SurfaceHolderCallback.shouldRecoverLocalSurface(
                true, true, 1080, 607, 1000, 560));
    }

    @Test
    public void recoveryRestartsSurfaceLifecycleWithoutChangingLayout() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/views/ExpandableSurfaceView.java"));

        assertTrue(source.contains("requestSurfaceRecreation"));
        assertTrue(source.contains("setVisibility(View.INVISIBLE)"));
        assertTrue(source.contains("setVisibility(View.VISIBLE)"));
        assertTrue(source.contains("SURFACE_RECOVERY_SETTLE_DELAY_MILLIS"));
        assertTrue(source.contains("SURFACE_RECREATE_GAP_MILLIS"));
    }
}
