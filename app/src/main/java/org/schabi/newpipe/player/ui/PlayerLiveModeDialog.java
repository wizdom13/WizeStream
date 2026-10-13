package org.schabi.newpipe.player.ui;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;

/** Opens the current-stream live-mode selector without changing its saved default. */
final class PlayerLiveModeDialog {
    private PlayerLiveModeDialog() { }

    static boolean show(final Player player, final Context context) {
        if (!player.isLive()) {
            return false;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.default_live_mode_title)
                .setSingleChoiceItems(R.array.live_mode_descriptions,
                        player.isNormalLive() ? 0 : 1, (dialog, which) -> {
                            player.setNormalLive(which == 0);
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
        return true;
    }
}
