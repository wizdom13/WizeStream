/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.Intent
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.util.function.Supplier
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.settings.SettingsActivity

/** Foreground shortcuts also work with a hardware keyboard, but never while entering text. */
class TvRemoteKeyDispatcher @JvmOverloads constructor(
    private val activity: AppCompatActivity,
    private val playerProvider: Supplier<Player?> = Supplier { PlayerHolder.getInstance().player.orElse(null) }
) {
    private val keys by lazy { TvRemoteKeys(PreferenceManager.getDefaultSharedPreferences(activity)) }
    private val press = TvRemoteKeyPress()

    fun dispatch(event: KeyEvent): Boolean = press.dispatch(
        event.keyCode,
        event.deviceId,
        event.action,
        event.repeatCount,
        event.isCanceled,
        activity.hasWindowFocus() && activity.currentFocus?.onCheckIsTextEditor() != true &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed,
        { code ->
            keys.actionFor(code)?.takeIf {
                (!it.playback || activePlayer() != null) && (
                    !TvRemoteKeys.isDirection(code) ||
                        activePlayer()?.UIs()?.get(MainPlayerUi::class.java)?.map { ui -> ui.isFullscreen && !ui.isControlsVisible }?.orElse(false) == true
                    )
            }
        },
        ::perform
    )

    fun clear() = press.clear()

    private fun activePlayer(): Player? = playerProvider.get()
        ?.takeIf { it.playQueue != null && it.currentState != Player.STATE_BLOCKED }

    private fun perform(action: TvRemoteAction) {
        if (action.playback) {
            val player = activePlayer() ?: return
            when (action) {
                TvRemoteAction.PLAY_PAUSE -> player.playPause()
                TvRemoteAction.REWIND -> player.fastRewind()
                TvRemoteAction.FORWARD -> player.fastForward()
                TvRemoteAction.PREVIOUS -> player.playPrevious()
                TvRemoteAction.NEXT -> player.playNext()
                TvRemoteAction.MUTE -> player.toggleMute()
                else -> Unit
            }
        } else if (action == TvRemoteAction.SETTINGS) {
            if (activity !is SettingsActivity) NavigationHelper.openSettings(activity)
        } else if (activity is MainActivity) {
            activity.openRemoteDestination(action)
        } else {
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_DESTINATION, action.id)
            )
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "org.schabi.newpipe.REMOTE_DESTINATION"
    }
}
