package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.player.PlaybackPresentationMode;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainPlayerUiTvReplayTest {
    @Test
    public void tvVideoIntentsRestoreFullscreenAfterExitWithoutRotationOrSheetCallbacks()
            throws Exception {
        final Context context = mock(Context.class);
        final Player player = mock(Player.class);
        final MainPlayerUi ui = mock(MainPlayerUi.class, CALLS_REAL_METHODS);
        final AtomicBoolean fullscreen = new AtomicBoolean(false);
        setField(ui, "context", context);
        setField(ui, "player", player);
        when(player.getPlaybackPresentationMode()).thenReturn(PlaybackPresentationMode.VIDEO);
        doAnswer(invocation -> {
            fullscreen.set(invocation.getArgument(0));
            return null;
        }).when(ui).setFullscreen(true);
        final Method open = MainPlayerUi.class.getDeclaredMethod("directlyOpenFullscreenIfNeeded");
        open.setAccessible(true);

        try (MockedStatic<DeviceUtils> devices = mockStatic(DeviceUtils.class);
             MockedStatic<PlayerHelper> helpers = mockStatic(PlayerHelper.class)) {
            devices.when(() -> DeviceUtils.isTv(context)).thenReturn(true);
            open.invoke(ui);
            assertTrue(fullscreen.get());
            fullscreen.set(false);
            open.invoke(ui);
            assertTrue(fullscreen.get());
            verify(player, never()).getFragmentListener();
            helpers.verifyNoInteractions();
        }
    }

    private static void setField(final MainPlayerUi ui, final String name, final Object value)
            throws Exception {
        final Field field = PlayerUi.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(ui, value);
    }
}
