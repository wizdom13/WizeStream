package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Binder;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.widget.PopupMenu;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PlayerPopupMenuTest {
    @Test
    public void detachedAnchorDoesNotOpenMenu() {
        final PopupMenu menu = mock(PopupMenu.class);
        assertFalse(PlayerPopupMenu.show(menu, mock(View.class)));
        verify(menu, never()).show();
    }

    @Test
    public void visibleAttachedAnchorOpensMenu() {
        final PopupMenu menu = mock(PopupMenu.class);
        assertTrue(PlayerPopupMenu.show(menu, attachedAnchor()));
        verify(menu).show();
    }

    @Test
    public void windowDisappearingDuringShowIsHandled() {
        final PopupMenu menu = mock(PopupMenu.class);
        doThrow(new WindowManager.BadTokenException()).when(menu).show();
        assertFalse(PlayerPopupMenu.show(menu, attachedAnchor()));
        verify(menu).dismiss();
    }

    private static View attachedAnchor() {
        final View anchor = mock(View.class);
        when(anchor.isAttachedToWindow()).thenReturn(true);
        when(anchor.isShown()).thenReturn(true);
        when(anchor.getWindowToken()).thenReturn(new Binder());
        return anchor;
    }
}
