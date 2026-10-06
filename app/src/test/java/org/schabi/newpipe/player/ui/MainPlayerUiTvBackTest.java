package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.KeyEvent;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.reflect.Field;

public class MainPlayerUiTvBackTest {
    @Test
    public void backExitsFullscreenWhenNoOverlayIsOpen() throws Exception {
        verifyBack(false, false);
    }

    @Test
    public void backDismissesControlsBeforeLeavingFullscreen() throws Exception {
        verifyBack(false, true);
    }

    @Test
    public void backClosesTheListBeforeControlsOrFullscreen() throws Exception {
        verifyBack(true, true);
    }

    private static void verifyBack(final boolean listOpen, final boolean controlsVisible)
            throws Exception {
        final Context context = mock(Context.class);
        final MainPlayerUi ui = mock(MainPlayerUi.class, CALLS_REAL_METHODS);
        final Field contextField = PlayerUi.class.getDeclaredField("context");
        contextField.setAccessible(true);
        contextField.set(ui, context);
        final Field fullscreenField = MainPlayerUi.class.getDeclaredField("isFullscreen");
        fullscreenField.setAccessible(true);
        fullscreenField.setBoolean(ui, true);
        doReturn(listOpen).when(ui).isAnyListViewOpen();
        doReturn(controlsVisible).when(ui).isControlsVisible();
        doNothing().when(ui).closeItemsList();
        doNothing().when(ui).hideControls(0, 0);
        doNothing().when(ui).toggleFullscreenWithOrientation();
        try (MockedStatic<DeviceUtils> devices = mockStatic(DeviceUtils.class)) {
            devices.when(() -> DeviceUtils.isTv(context)).thenReturn(true);
            assertTrue(ui.onKeyDown(KeyEvent.KEYCODE_BACK));
            if (listOpen) {
                verify(ui).closeItemsList();
                verify(ui, never()).hideControls(0, 0);
            } else if (controlsVisible) {
                verify(ui).hideControls(0, 0);
                verify(ui, never()).closeItemsList();
            } else {
                verify(ui).toggleFullscreenWithOrientation();
                verify(ui, never()).closeItemsList();
                verify(ui, never()).hideControls(0, 0);
            }
            if (listOpen || controlsVisible) {
                verify(ui, never()).toggleFullscreenWithOrientation();
            }
        }
    }
}
