package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.PopupMenu;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerPopupMenuTest {
    private ActivityController<Activity> activity;
    private Button anchor;
    private PopupMenu menu;
    private PlayerPopupMenu controller;

    @Before
    public void setup() {
        activity = Robolectric.buildActivity(Activity.class);
        activity.get().setTheme(R.style.LightTheme);
        activity.setup().visible();
        anchor = new Button(activity.get());
        activity.get().setContentView(anchor);
        anchor.layout(0, 0, 120, 48);
        menu = new PopupMenu(activity.get(), anchor);
        menu.getMenu().add(0, 1, 0, "Auto");
        menu.getMenu().add(0, 2, 1, "1080p");
        controller = new PlayerPopupMenu();
    }

    @After
    public void teardown() {
        controller.dismiss();
        activity.pause().stop().destroy();
    }

    @Test
    public void detachedAnchorDoesNotOpenMenu() {
        final PopupMenu model = mock(PopupMenu.class);
        assertFalse(controller.show(model, mock(View.class), () -> { }));
        verify(model, never()).show();
    }

    @Test
    public void selectingFlatChoiceInvokesOriginalActionAndCanReopen() throws Exception {
        final int[] selected = {0};
        final int[] dismissed = {0};
        menu.setOnMenuItemClickListener(item -> {
            selected[0] = item.getItemId();
            return true;
        });
        assertTrue(controller.show(menu, anchor, () -> dismissed[0]++));
        popup().getListView().performItemClick(null, 1, 1);
        assertEquals(2, selected[0]);
        assertEquals(1, dismissed[0]);
        assertTrue(controller.show(menu, anchor, () -> dismissed[0]++));
        controller.dismiss();
        assertEquals(2, dismissed[0]);
    }

    @Test
    public void replacingMenuDismissesOnceAndPreservesDisabledChoices() throws Exception {
        menu.getMenu().findItem(2).setEnabled(false);
        final int[] dismissed = {0};
        assertTrue(controller.show(menu, anchor, () -> dismissed[0]++));
        final ListPopupWindow old = popup();
        assertFalse(old.getListView().getAdapter().isEnabled(1));
        assertTrue(controller.show(menu, anchor, () -> dismissed[0]++));
        assertFalse(old.isShowing());
        assertEquals(1, dismissed[0]);
    }

    private ListPopupWindow popup() throws Exception {
        final java.lang.reflect.Field field = PlayerPopupMenu.class.getDeclaredField("popup");
        field.setAccessible(true);
        return (ListPopupWindow) field.get(controller);
    }
}
