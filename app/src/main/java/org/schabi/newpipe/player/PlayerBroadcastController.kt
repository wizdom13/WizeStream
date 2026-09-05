/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_CLOSE
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_FORWARD
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_REWIND
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_NEXT
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PREVIOUS
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_REPEAT
import org.schabi.newpipe.player.notification.NotificationConstants.ACTION_SHUFFLE

/** Owns player broadcast registration, lifecycle, and notification action routing. */
internal class PlayerBroadcastController(private val player: Player) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handle(intent)
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        addAction(ACTION_CLOSE)
        addAction(ACTION_PLAY_PAUSE)
        addAction(ACTION_PLAY_PREVIOUS)
        addAction(ACTION_PLAY_NEXT)
        addAction(ACTION_FAST_REWIND)
        addAction(ACTION_FAST_FORWARD)
        addAction(ACTION_REPEAT)
        addAction(ACTION_SHUFFLE)
        addAction(ACTION_RECREATE_NOTIFICATION)
        addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED)
        addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED)
        addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_HEADSET_PLUG)
    }

    var isScreenOn = true
        private set

    fun register() {
        unregister()
        ContextCompat.registerReceiver(
            player.context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregister() {
        try {
            player.context.unregisterReceiver(receiver)
        } catch (unregisteredException: IllegalArgumentException) {
            Log.w(
                Player.TAG,
                "Broadcast receiver already unregistered: ${unregisteredException.message}"
            )
        }
    }

    private fun handle(intent: Intent?) {
        val action = intent?.action ?: return
        if (Player.DEBUG) {
            Log.d(Player.TAG, "onBroadcastReceived() called with: intent = [$intent]")
        }

        when (action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> player.pause()
            ACTION_CLOSE -> player.service.destroyPlayerAndStopService()
            ACTION_PLAY_PAUSE -> player.playPause()
            ACTION_PLAY_PREVIOUS -> player.playPrevious()
            ACTION_PLAY_NEXT -> player.playNext()
            ACTION_FAST_REWIND -> player.fastRewind()
            ACTION_FAST_FORWARD -> player.fastForward()
            ACTION_REPEAT -> player.cycleNextRepeatMode()
            ACTION_SHUFFLE -> player.toggleShuffleModeEnabled()
            Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            Intent.ACTION_SCREEN_ON -> isScreenOn = true
            Intent.ACTION_CONFIGURATION_CHANGED -> logConfigurationChanged()
        }

        player.UIs().call { ui -> ui.onBroadcastReceived(intent) }
    }

    private fun logConfigurationChanged() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "ACTION_CONFIGURATION_CHANGED received")
        }
    }
}
