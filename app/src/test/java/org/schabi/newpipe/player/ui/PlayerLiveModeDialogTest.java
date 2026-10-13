package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerLiveModeDialogTest {
    @Test
    public void applicationContextUsesAttachedPlayerTokenAndSelectsMode() {
        try (ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class)) {
            activity.get().setTheme(R.style.LightTheme);
            activity.setup().visible();
            final Button anchor = new Button(new ContextThemeWrapper(
                    activity.get().getApplicationContext(), R.style.LightTheme));
            activity.get().setContentView(anchor);
            anchor.layout(0, 0, 120, 48);
            final Player player = mock(Player.class);
            when(player.isLive()).thenReturn(true);
            when(player.isNormalLive()).thenReturn(true);

            assertTrue(PlayerLiveModeDialog.show(player, anchor));
            final AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
                    dialog.getWindow().getAttributes().type);
            assertSame(anchor.getWindowToken(), dialog.getWindow().getAttributes().token);
            dialog.getListView().performItemClick(null, 1, 1);
            verify(player).setNormalLive(false);
            assertFalse(dialog.isShowing());

            assertTrue(PlayerLiveModeDialog.show(player, anchor));
            final AlertDialog reopened = (AlertDialog) ShadowDialog.getLatestDialog();
            activity.get().setContentView(new View(activity.get()));
            assertFalse(reopened.isShowing());
        }
    }

    @Test
    public void detachedPlayerDoesNotOpenDialog() {
        final Player player = mock(Player.class);
        when(player.isLive()).thenReturn(true);
        assertFalse(PlayerLiveModeDialog.show(player, mock(View.class)));
    }
}
