package org.schabi.newpipe.player.ui;

import android.view.View;
import android.widget.ArrayAdapter;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;

/** Opens the current-stream live-mode selector without changing its saved default. */
final class PlayerLiveModeDialog {
    private PlayerLiveModeDialog() { }

    static boolean show(final Player player, final View anchor) {
        if (!player.isLive() || !anchor.isAttachedToWindow() || !anchor.isShown()
                || anchor.getWindowToken() == null) {
            return false;
        }
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
                PlayerPopupMenu.dialogContext(anchor));
        final ArrayAdapter<String> modes = new ArrayAdapter<>(builder.getContext(),
                R.layout.player_single_choice_row,
                anchor.getResources().getStringArray(R.array.live_mode_descriptions));
        final AlertDialog choices = builder
                .setTitle(R.string.default_live_mode_title)
                .setSingleChoiceItems(modes,
                        player.isNormalLive() ? 0 : 1, (dialog, which) -> {
                            player.setNormalLive(which == 0);
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.cancel, null)
                .create();
        final Window window = choices.getWindow();
        if (window == null) {
            return false;
        }
        final WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        attributes.token = anchor.getWindowToken();
        window.setAttributes(attributes);
        final View.OnAttachStateChangeListener listener =
                PlayerPopupMenu.detachListener(choices::dismiss);
        anchor.addOnAttachStateChangeListener(listener);
        choices.setOnDismissListener(ignored -> anchor.removeOnAttachStateChangeListener(listener));
        try {
            choices.show();
            return true;
        } catch (final WindowManager.BadTokenException exception) {
            anchor.removeOnAttachStateChangeListener(listener);
            choices.dismiss();
            return false;
        }
    }
}
