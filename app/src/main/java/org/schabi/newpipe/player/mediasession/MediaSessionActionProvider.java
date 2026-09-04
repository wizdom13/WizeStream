package org.schabi.newpipe.player.mediasession;

import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_CLOSE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_REPEAT;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_SHUFFLE;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.media3.session.CommandButton;
import androidx.media3.session.SessionCommand;

import org.schabi.newpipe.player.notification.NotificationActionData;

import java.util.Set;

/** Converts WizeStream notification actions to Media3 session commands. */
public final class MediaSessionActionProvider {
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            ACTION_CLOSE,
            ACTION_FAST_FORWARD,
            ACTION_FAST_REWIND,
            ACTION_PLAY_NEXT,
            ACTION_PLAY_PAUSE,
            ACTION_PLAY_PREVIOUS,
            ACTION_REPEAT,
            ACTION_SHUFFLE
    );

    private MediaSessionActionProvider() {
    }

    @NonNull
    public static SessionCommand commandFor(@NonNull final String action) {
        return new SessionCommand(action, Bundle.EMPTY);
    }

    @NonNull
    public static CommandButton buttonFor(@NonNull final NotificationActionData data,
                                          final boolean backwardSlot) {
        return new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(data.name())
                .setCustomIconResId(data.icon())
                .setSessionCommand(commandFor(data.action()))
                .setSlots(backwardSlot
                        ? CommandButton.SLOT_BACK_SECONDARY
                        : CommandButton.SLOT_FORWARD_SECONDARY,
                        CommandButton.SLOT_OVERFLOW)
                .build();
    }

    public static boolean isSupported(@NonNull final SessionCommand command) {
        return SUPPORTED_ACTIONS.contains(command.customAction);
    }

    @NonNull
    public static Set<String> supportedActions() {
        return SUPPORTED_ACTIONS;
    }
}
