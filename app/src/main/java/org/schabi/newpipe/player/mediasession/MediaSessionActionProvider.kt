package org.schabi.newpipe.player.mediasession

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import java.util.Collections
import org.schabi.newpipe.player.notification.NotificationActionData
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_CLOSE
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_FORWARD
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_REWIND
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_NEXT
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PREVIOUS
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_REPEAT
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_SHUFFLE

/** Converts WizeStream notification actions to Media3 session commands. */
class MediaSessionActionProvider private constructor() {
    companion object {
        private val SUPPORTED_ACTIONS: Set<String> = Collections.unmodifiableSet(
            linkedSetOf(
                ACTION_CLOSE,
                ACTION_FAST_FORWARD,
                ACTION_FAST_REWIND,
                ACTION_PLAY_NEXT,
                ACTION_PLAY_PAUSE,
                ACTION_PLAY_PREVIOUS,
                ACTION_REPEAT,
                ACTION_SHUFFLE
            )
        )

        @JvmStatic
        fun commandFor(action: String): SessionCommand = SessionCommand(action, Bundle.EMPTY)

        @JvmStatic
        fun buttonFor(data: NotificationActionData, backwardSlot: Boolean): CommandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(data.name())
            .setCustomIconResId(data.icon())
            .setSessionCommand(commandFor(data.action()))
            .setSlots(
                if (backwardSlot) {
                    CommandButton.SLOT_BACK_SECONDARY
                } else {
                    CommandButton.SLOT_FORWARD_SECONDARY
                },
                CommandButton.SLOT_OVERFLOW
            )
            .build()

        @JvmStatic
        fun isSupported(command: SessionCommand): Boolean = SUPPORTED_ACTIONS.contains(command.customAction)

        @JvmStatic
        fun supportedActions(): Set<String> = SUPPORTED_ACTIONS
    }
}
