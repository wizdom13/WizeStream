package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.PlayerUiList;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoDetailBackPressTest {
    private MockedStatic<Log> log;
    private MockedStatic<DeviceUtils> deviceUtils;
    private MockedStatic<PlayerHelper> playerHelper;
    private VideoDetailFragment fragment;
    private MainPlayerUi playerUi;
    private Player player;
    private AppCompatActivity activity;
    private Configuration configuration;
    private final AtomicBoolean fullscreen = new AtomicBoolean(true);

    @Before
    public void setUp() throws Exception {
        log = mockStatic(Log.class);
        deviceUtils = mockStatic(DeviceUtils.class);
        playerHelper = mockStatic(PlayerHelper.class);
        // Skip Android view construction, but execute the real Back and rotation handlers.
        fragment = mock(VideoDetailFragment.class, CALLS_REAL_METHODS);
        player = mock(Player.class);
        playerUi = mock(MainPlayerUi.class);
        activity = mock(AppCompatActivity.class);
        configuration = mock(Configuration.class);
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;
        final Resources resources = mock(Resources.class);
        when(resources.getConfiguration()).thenReturn(configuration);
        doReturn(resources).when(fragment).getResources();
        doReturn(activity).when(fragment).requireContext();
        setField(BaseFragment.class, fragment, "activity", activity);
        setField(VideoDetailFragment.class, fragment, "player", player);
        setField(PlayerUi.class, playerUi, "player", player);
        setField(PlayerUi.class, playerUi, "context", activity);

        when(player.UIs()).thenReturn(new PlayerUiList(playerUi));
        when(player.getFragmentListener()).thenReturn(Optional.of(fragment));
        when(playerUi.isFullscreen()).thenAnswer(invocation -> fullscreen.get());
        when(playerUi.isLandscape()).thenReturn(true);
        doAnswer(invocation -> {
            fullscreen.set(invocation.getArgument(0));
            return null;
        }).when(playerUi).setFullscreen(anyBoolean());
        doAnswer(invocation -> {
            fullscreen.set(!fullscreen.get());
            return null;
        }).when(playerUi).toggleFullscreen();
        doCallRealMethod().when(playerUi).toggleFullscreenWithOrientation();
        deviceUtils.when(() -> DeviceUtils.isLandscape(activity)).thenReturn(true);
    }

    @After
    public void tearDown() {
        if (playerHelper != null) {
            playerHelper.close();
        }
        if (deviceUtils != null) {
            deviceUtils.close();
        }
        if (log != null) {
            log.close();
        }
    }

    @Test
    public void backWaitsForPortraitBeforeExitingFullscreenWithoutPausing() throws Exception {
        assertTrue(fragment.onBackPressed());
        verify(activity).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        verify(playerUi, never()).toggleFullscreen();

        // A surface reattachment may still see landscape while Android handles the request.
        syncFullscreenWithOrientation();
        verify(playerUi, never()).setFullscreen(anyBoolean());
        assertTrue(fullscreen.get());

        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        syncFullscreenWithOrientation();
        verify(playerUi).setFullscreen(false);
        verify(playerUi, never()).setFullscreen(true);
        assertFalse(fullscreen.get());
        verify(player, never()).pause();
        verify(player, never()).getPlayQueue();
        verify(fragment).setAutoPlay(false);
    }

    @Test
    public void tabletBackExitsFullscreenWithoutRequestingPortrait() {
        deviceUtils.when(() -> DeviceUtils.isTablet(activity)).thenReturn(true);

        assertTrue(fragment.onBackPressed());

        assertFalse(fullscreen.get());
        verify(playerUi).setFullscreen(false);
        verify(playerUi, never()).toggleFullscreen();
        verify(activity, never()).setRequestedOrientation(anyInt());
        verify(player, never()).pause();
        verify(player, never()).getPlayQueue();
    }

    private void syncFullscreenWithOrientation() throws Exception {
        final Method method = VideoDetailFragment.class.getDeclaredMethod(
                "syncFullscreenWithOrientation", Optional.class);
        method.setAccessible(true);
        method.invoke(fragment, Optional.of(playerUi));
    }

    private static void setField(final Class<?> type, final Object target,
                                 final String name, final Object value) throws Exception {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
