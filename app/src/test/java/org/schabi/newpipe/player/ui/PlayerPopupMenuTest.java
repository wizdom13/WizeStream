package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.PopupMenu;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
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

    @Test
    public void desktopDialogShowsChoicesAndInvokesTheOriginalAction() throws Exception {
        final int[] selected = {0};
        final int[] dismissed = {0};
        menu.setOnMenuItemClickListener(item -> {
            selected[0] = item.getItemId();
            return true;
        });
        assertTrue(controller.show(menu, anchor, () -> dismissed[0]++, true));
        final java.lang.reflect.Field field = PlayerPopupMenu.class.getDeclaredField("dialog");
        field.setAccessible(true);
        final AlertDialog dialog = (AlertDialog) field.get(controller);
        assertTrue(dialog.isShowing());
        assertEquals(2, dialog.getListView().getAdapter().getCount());
        dialog.getListView().performItemClick(null, 1, 1);
        assertEquals(2, selected[0]);
        controller.dismiss();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, dismissed[0]);
        assertTrue(controller.show(menu, anchor, () -> dismissed[0]++, true));
    }

    @Test
    public void desktopDialogUsesPlayerWindowTokenWithApplicationContext() throws Exception {
        final ContextThemeWrapper context = new ContextThemeWrapper(
                activity.get().getApplicationContext(), R.style.LightTheme);
        anchor = new Button(context);
        activity.get().setContentView(anchor);
        anchor.layout(0, 0, 120, 48);
        menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 0, "Auto");
        menu.getMenu().add(0, 2, 1, "1080p");
        final int[] selected = {0};
        menu.setOnMenuItemClickListener(item -> {
            selected[0] = item.getItemId();
            return true;
        });

        assertTrue(controller.show(menu, anchor, () -> { }, true));
        final java.lang.reflect.Field field = PlayerPopupMenu.class.getDeclaredField("dialog");
        field.setAccessible(true);
        final AlertDialog dialog = (AlertDialog) field.get(controller);
        assertTrue(dialog.isShowing());
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
                dialog.getWindow().getAttributes().type);
        assertSame(anchor.getWindowToken(), dialog.getWindow().getAttributes().token);
        dialog.getListView().performItemClick(null, 1, 1);
        assertEquals(2, selected[0]);
    }

    @Test
    @Config(sdk = {23, 25, 28, 35})
    public void desktopDialogRepairsAnUnthemedApplicationAnchor() throws Exception {
        final android.content.res.Resources.Theme platformTheme =
                activity.get().getResources().newTheme();
        platformTheme.applyStyle(android.R.style.Theme_Material_Light_NoActionBar, true);
        final android.content.Context source = new android.content.ContextWrapper(
                activity.get().getApplicationContext()) {
            @Override
            public android.content.res.Resources.Theme getTheme() {
                return platformTheme;
            }
        };
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(source));
        anchor = new Button(source);
        activity.get().setContentView(anchor);
        anchor.layout(0, 0, 120, 48);
        menu = new PopupMenu(activity.get(), anchor);
        menu.getMenu().add(0, 1, 0, "Auto");
        final int[] selected = {0};
        menu.setOnMenuItemClickListener(item -> {
            selected[0] = item.getItemId();
            return true;
        });

        assertTrue(controller.show(menu, anchor, () -> { }, true));
        final java.lang.reflect.Field field = PlayerPopupMenu.class.getDeclaredField("dialog");
        field.setAccessible(true);
        final AlertDialog choices = (AlertDialog) field.get(controller);
        assertTrue(choices.isShowing());
        assertSame(anchor.getWindowToken(), choices.getWindow().getAttributes().token);
        choices.getListView().performItemClick(null, 0, 0);
        assertEquals(1, selected[0]);
    }

    @Test
    @Config(sdk = {23, 25, 28, 35})
    public void displayDialogKeepsTheAnchorsExplicitPalette() {
        final ContextThemeWrapper source = new ContextThemeWrapper(
                activity.get().getApplicationContext(), R.style.BlackTheme);
        source.getTheme().applyStyle(R.style.ThemeOverlay_wizestream_ThemeColor_Purple, true);
        final Button view = new Button(source);
        activity.get().setContentView(view);
        final android.content.Context result = PlayerPopupMenu.dialogContext(view);
        assertEquals(org.schabi.newpipe.util.ThemeHelper.resolveColorFromAttr(
                source, R.attr.colorPrimary),
                org.schabi.newpipe.util.ThemeHelper.resolveColorFromAttr(
                        result, R.attr.colorPrimary));
        assertEquals(org.schabi.newpipe.util.ThemeHelper.resolveColorFromAttr(
                source, com.google.android.material.R.attr.colorSurface),
                org.schabi.newpipe.util.ThemeHelper.resolveColorFromAttr(
                        result, com.google.android.material.R.attr.colorSurface));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(result).create();
    }

    private ListPopupWindow popup() throws Exception {
        final java.lang.reflect.Field field = PlayerPopupMenu.class.getDeclaredField("popup");
        field.setAccessible(true);
        return (ListPopupWindow) field.get(controller);
    }
}
