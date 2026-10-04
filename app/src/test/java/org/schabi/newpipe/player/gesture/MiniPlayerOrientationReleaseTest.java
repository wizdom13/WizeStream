package org.schabi.newpipe.player.gesture;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.View;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.reflect.Method;

public class MiniPlayerOrientationReleaseTest {
    @Test
    public void collapseReleasesPhoneRotationEvenAfterNavigationAlreadyExitedFullscreen()
            throws Exception {
        verifyCollapse(false, false, false, false);
    }

    @Test
    public void collapseAlsoReleasesRotationWhenItExitsFullscreen() throws Exception {
        verifyCollapse(false, false, false, true);
    }

    @Test
    public void largeScreenAndTvOrientationsAreNotChanged() throws Exception {
        verifyCollapse(true, false, false, false);
        verifyCollapse(false, true, false, false);
        verifyCollapse(false, false, true, false);
    }

    private static void verifyCollapse(final boolean tablet, final boolean tv,
                                       final boolean desktop, final boolean wasFullscreen)
            throws Exception {
        final Activity activity = mock(Activity.class);
        final View sheet = mock(View.class);
        when(sheet.getContext()).thenReturn(activity);
        final PlayerHolder holder = mock(PlayerHolder.class);
        when(holder.exitMainPlayerFullscreenForMiniPlayer()).thenReturn(wasFullscreen);
        try (MockedStatic<DeviceUtils> devices = mockStatic(DeviceUtils.class);
             MockedStatic<PlayerHolder> holders = mockStatic(PlayerHolder.class)) {
            devices.when(() -> DeviceUtils.isTablet(activity)).thenReturn(tablet);
            devices.when(() -> DeviceUtils.isTv(activity)).thenReturn(tv);
            devices.when(() -> DeviceUtils.isDesktopMode(activity)).thenReturn(desktop);
            holders.when(PlayerHolder::getInstance).thenReturn(holder);
            final CustomBottomSheetBehavior behavior =
                    mock(CustomBottomSheetBehavior.class, CALLS_REAL_METHODS);
            final Method collapse = CustomBottomSheetBehavior.class.getDeclaredMethod(
                    "restorePhoneOrientationAfterFullscreenCollapse", View.class);
            collapse.setAccessible(true);
            collapse.invoke(behavior, sheet);

            if (tablet || tv || desktop) {
                verify(activity, never()).setRequestedOrientation(anyInt());
                verify(holder, never()).exitMainPlayerFullscreenForMiniPlayer();
            } else {
                verify(activity).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                verify(holder).exitMainPlayerFullscreenForMiniPlayer();
            }
        }
    }
}
