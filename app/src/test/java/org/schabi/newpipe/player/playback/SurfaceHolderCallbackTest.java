package org.schabi.newpipe.player.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.media3.common.Player;

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
    public void surfaceRecoveryIsNotRestrictedToLocalMedia() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/playback/SurfaceHolderCallback.java"));

        assertFalse(source.contains("isLocalMediaUri"));
        assertFalse(source.contains("requestSurfaceRecreation"));
    }

    @Test
    public void expandableSurfaceFollowsAttachmentLifecycleOnAndroid14Plus() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/views/ExpandableSurfaceView.java"));

        assertTrue(source.contains("SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT"));
        assertFalse(source.contains("requestSurfaceRecreation"));
        assertFalse(source.contains("setVisibility(View.INVISIBLE)"));
    }

    @Test
    public void detailFragmentKeepsPlayerAttachedToTheCorrectPlaceholder() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));

        assertTrue(source.contains("final View playerView = playerUi.getBinding().getRoot();"));
        assertTrue(source.contains(
                "if (playerView.getParent() != binding.playerPlaceholder)"));
        assertTrue(source.contains("binding.playerPlaceholder.addView(playerView);"));
    }
}
