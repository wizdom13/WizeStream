package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.media3.common.VideoSize;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.PlayerUiList;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoDetailResumeLayoutTest {
    private MockedStatic<DeviceUtils> deviceUtils;
    private MockedStatic<ViewCompat> viewCompat;
    private MockedStatic<OneShotPreDrawListener> preDraw;
    private MockedStatic<Log> log;
    private Runnable nextPreDraw;
    private VideoDetailFragment fragment;
    private MainPlayerUi playerUi;
    private Player player;
    private PlayerHolder playerHolder;
    private AppCompatActivity activity;
    private Configuration configuration;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private FrameLayout root;
    private final AtomicBoolean fullscreen = new AtomicBoolean(true);

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        deviceUtils = mockStatic(DeviceUtils.class);
        viewCompat = mockStatic(ViewCompat.class);
        log = mockStatic(Log.class);
        preDraw = mockStatic(OneShotPreDrawListener.class);
        preDraw.when(() -> OneShotPreDrawListener.add(any(View.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    nextPreDraw = invocation.getArgument(1);
                    return mock(OneShotPreDrawListener.class);
                });
        // Retain the player and its fullscreen state across a missed configuration callback.
        fragment = mock(VideoDetailFragment.class, CALLS_REAL_METHODS);
        player = mock(Player.class);
        playerUi = mock(MainPlayerUi.class);
        activity = mock(AppCompatActivity.class);
        configuration = mock(Configuration.class);
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        final Resources resources = mock(Resources.class);
        when(resources.getConfiguration()).thenReturn(configuration);
        doReturn(resources).when(fragment).getResources();
        doReturn(true).when(fragment).isAdded();
        doReturn(true).when(fragment).isResumed();
        root = mock(FrameLayout.class);
        final View playerView = mock(View.class);
        when(playerView.getParent()).thenReturn(mock(ViewGroup.class));
        doReturn(Optional.of(playerView)).when(fragment).getRoot();
        final FragmentVideoDetailBinding binding = mock(FragmentVideoDetailBinding.class);
        when(binding.getRoot()).thenReturn(root);
        bottomSheet = mock(BottomSheetBehavior.class);
        when(bottomSheet.getState()).thenReturn(BottomSheetBehavior.STATE_EXPANDED);
        setField(BaseFragment.class, fragment, "activity", activity);
        setField(VideoDetailFragment.class, fragment, "binding", binding);
        setField(VideoDetailFragment.class, fragment, "player", player);
        final PlayerService service = mock(PlayerService.class);
        setField(VideoDetailFragment.class, fragment, "playerService", service);
        setField(VideoDetailFragment.class, fragment, "bottomSheetBehavior", bottomSheet);
        setField(VideoDetailFragment.class, fragment, "lastStableBottomSheetState",
                BottomSheetBehavior.STATE_EXPANDED);
        setField(PlayerUi.class, playerUi, "player", player);
        when(player.UIs()).thenReturn(new PlayerUiList(playerUi));
        // Register through the real holder: the player's listener is a forwarding listener,
        // not the fragment itself. Bypassing this path hid the missing resume callback.
        final Constructor<PlayerHolder> constructor = PlayerHolder.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        playerHolder = constructor.newInstance();
        setField(PlayerHolder.class, playerHolder, "playerService", service);
        when(service.getPlayer()).thenReturn(player);
        doNothing().when(fragment).onServiceConnected(service);
        doAnswer(invocation -> {
            final PlayerServiceEventListener listener = invocation.getArgument(0);
            when(player.getFragmentListener()).thenReturn(Optional.of(listener));
            return null;
        }).when(player).setFragmentListener(any(PlayerServiceEventListener.class));
        playerHolder.setListener(fragment);
        when(player.videoPlayerSelected()).thenReturn(true);
        when(playerUi.isFullscreen()).thenAnswer(invocation -> fullscreen.get());
        doAnswer(invocation -> {
            fullscreen.set(invocation.getArgument(0));
            return null;
        }).when(playerUi).setFullscreen(anyBoolean());
        // Observe the layout application separately from Android view rendering.
        doNothing().when(fragment).refreshFullscreenLayout(anyBoolean());
    }

    @After
    public void tearDown() {
        if (preDraw != null) {
            preDraw.close();
        }
        if (log != null) {
            log.close();
        }
        if (viewCompat != null) {
            viewCompat.close();
        }
        if (deviceUtils != null) {
            deviceUtils.close();
        }
    }

    @Test
    public void portraitWakeExitsFullscreenBeforeRestoringLayout() {
        fragment.restorePlayerLayoutAfterResume();

        final InOrder order = inOrder(playerUi, fragment);
        order.verify(playerUi).setFullscreen(false);
        order.verify(fragment).refreshFullscreenLayout(false);
        assertFalse(fullscreen.get());
        viewCompat.verify(() -> ViewCompat.requestApplyInsets(root));
        verify(player, never()).pause();
        verify(player, never()).play();
    }

    @Test
    public void alreadyPortraitPlayerStillRestoresStaleGeometryAndInsets() {
        fullscreen.set(false);

        fragment.restorePlayerLayoutAfterResume();

        verify(playerUi, never()).setFullscreen(anyBoolean());
        verify(fragment).refreshFullscreenLayout(false);
        viewCompat.verify(() -> ViewCompat.requestApplyInsets(root));
        verify(fragment, never()).scrollToTop();
    }

    @Test
    public void resumedOrientationSupersedesAnUnfinishedPreLockRequest() throws Exception {
        setField(VideoDetailFragment.class, fragment, "pendingFullscreenOrientation",
                Configuration.ORIENTATION_LANDSCAPE);

        fragment.restorePlayerLayoutAfterResume();
        fragment.restorePlayerLayoutAfterResume();

        assertFalse(fullscreen.get());
        verify(playerUi).setFullscreen(false);
        verify(playerUi, never()).setFullscreen(true);
        verify(fragment, times(2)).refreshFullscreenLayout(false);
    }

    @Test
    public void landscapeWakeKeepsFullscreen() {
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;

        fragment.restorePlayerLayoutAfterResume();

        assertTrue(fullscreen.get());
        verify(playerUi, never()).setFullscreen(false);
        verify(fragment).refreshFullscreenLayout(true);
    }

    @Test
    public void portraitFullscreenForVerticalVideoIsPreserved() {
        when(playerUi.isVerticalVideo()).thenReturn(true);

        fragment.restorePlayerLayoutAfterResume();

        assertTrue(fullscreen.get());
        verify(playerUi, never()).setFullscreen(anyBoolean());
        verify(fragment).refreshFullscreenLayout(true);
    }

    @Test
    public void tabletManualFullscreenIsPreserved() {
        deviceUtils.when(() -> DeviceUtils.isTablet(activity)).thenReturn(true);

        fragment.restorePlayerLayoutAfterResume();

        verify(playerUi, never()).setFullscreen(anyBoolean());
        verify(fragment).refreshFullscreenLayout(true);
    }

    @Test
    public void pipPreparationIsNotOverridden() throws Exception {
        setField(VideoDetailFragment.class, fragment, "nativePipPrepared", true);

        fragment.restorePlayerLayoutAfterResume();

        assertNoLayoutChange();
    }

    @Test
    public void collapsedPlayerDoesNotChangeTheForegroundLayout() {
        when(bottomSheet.getState()).thenReturn(BottomSheetBehavior.STATE_COLLAPSED);

        fragment.restorePlayerLayoutAfterResume();

        assertNoLayoutChange();
    }

    @Test
    public void audioOnlyPlaybackDoesNotRestoreTheVideoLayout() {
        when(player.isAudioOnly()).thenReturn(true);

        fragment.restorePlayerLayoutAfterResume();

        assertNoLayoutChange();
    }

    @Test
    public void delayedResumeBroadcastRecoversAfterTheFirstFrameSkippedAudioOnlyPlayback() {
        final AtomicBoolean audioOnly = simulateBackgroundVideo();
        fragment.restorePlayerLayoutAfterResume();
        assertNoLayoutChange();

        deliverResumeBroadcast();

        assertFalse(audioOnly.get());
        assertFalse(fullscreen.get());
        final InOrder order = inOrder(player, fragment, playerUi);
        order.verify(player).useVideoAndSubtitles(true);
        order.verify(fragment).onVideoPlaybackResumed();
        order.verify(playerUi).setFullscreen(false);
        order.verify(fragment).refreshFullscreenLayout(false);
        order.verify(playerUi).hideSystemUIIfNeeded();
        nextPreDraw.run();
        verify(fragment, times(2)).refreshFullscreenLayout(false);
        verify(player, never()).pause();
        verify(player, never()).play();
    }

    @Test
    public void rotationCanEnterAndExitFullscreenAgainAfterDelayedResume() {
        simulateBackgroundVideo();
        fragment.restorePlayerLayoutAfterResume();
        deliverResumeBroadcast();
        nextPreDraw.run();
        assertFalse(fullscreen.get());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        }).when(root).post(any(Runnable.class));

        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;
        fragment.onConfigurationChanged(configuration);
        assertTrue(fullscreen.get());

        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        fragment.onConfigurationChanged(configuration);
        assertFalse(fullscreen.get());
    }

    @Test
    public void resumeBroadcastBeforeFirstFrameAlsoRestoresPortraitLayout() {
        simulateBackgroundVideo();

        deliverResumeBroadcast();
        nextPreDraw.run();

        assertFalse(fullscreen.get());
        verify(playerUi).setFullscreen(false);
        verify(fragment, times(2)).refreshFullscreenLayout(false);
    }

    @Test
    public void delayedResumeBroadcastAfterPauseDoesNotChangeTheWindow() {
        simulateBackgroundVideo();
        doReturn(false).when(fragment).isResumed();

        deliverResumeBroadcast();

        assertNoLayoutChange();
        preDraw.verifyNoInteractions();
    }

    @Test
    public void delayedResumeBroadcastWithDetachedListenerDoesNotChangeTheWindow() {
        simulateBackgroundVideo();
        playerHolder.setListener(null);

        deliverResumeBroadcast();

        verify(fragment, never()).onVideoPlaybackResumed();
        assertNoLayoutChange();
        preDraw.verifyNoInteractions();
    }

    @Test
    public void delayedResumeBroadcastReachesOnlyTheCurrentListener() {
        simulateBackgroundVideo();
        final PlayerServiceExtendedEventListener replacement =
                mock(PlayerServiceExtendedEventListener.class);
        playerHolder.setListener(replacement);

        deliverResumeBroadcast();

        verify(replacement).onVideoPlaybackResumed();
        verify(fragment, never()).onVideoPlaybackResumed();
        assertNoLayoutChange();
        preDraw.verifyNoInteractions();
    }

    @Test
    public void unknownVideoSizeOnScreenOffPreservesVerticalFullscreen() throws Exception {
        setField(MainPlayerUi.class, playerUi, "isVerticalVideo", true);
        doCallRealMethod().when(playerUi).isVerticalVideo();
        doCallRealMethod().when(playerUi).onVideoSizeChanged(any(VideoSize.class));

        playerUi.onVideoSizeChanged(VideoSize.UNKNOWN);
        fragment.restorePlayerLayoutAfterResume();

        assertTrue(playerUi.isVerticalVideo());
        assertTrue(fullscreen.get());
        verify(playerUi, never()).setFullscreen(anyBoolean());
        verify(fragment, never()).onScreenRotationButtonClicked();
    }

    @Test
    public void callbackAfterPauseDoesNotChangeTheWindow() {
        doReturn(false).when(fragment).isResumed();

        fragment.restorePlayerLayoutAfterResume();

        assertNoLayoutChange();
    }

    @Test
    public void callbackAfterViewDestructionDoesNothing() throws Exception {
        setField(VideoDetailFragment.class, fragment, "binding", null);

        fragment.restorePlayerLayoutAfterResume();

        assertNoLayoutChange();
    }

    private void assertNoLayoutChange() {
        verify(playerUi, never()).setFullscreen(anyBoolean());
        verify(fragment, never()).refreshFullscreenLayout(anyBoolean());
        viewCompat.verifyNoInteractions();
    }

    private AtomicBoolean simulateBackgroundVideo() {
        final AtomicBoolean audioOnly = new AtomicBoolean(true);
        when(player.isAudioOnly()).thenAnswer(invocation -> audioOnly.get());
        doAnswer(invocation -> {
            audioOnly.set(false);
            return null;
        }).when(player).useVideoAndSubtitles(true);
        return audioOnly;
    }

    private void deliverResumeBroadcast() {
        final Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED);
        doCallRealMethod().when(playerUi).onBroadcastReceived(intent);
        playerUi.onBroadcastReceived(intent);
    }

    private static void setField(final Class<?> type, final Object target,
                                 final String name, final Object value) throws Exception {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
