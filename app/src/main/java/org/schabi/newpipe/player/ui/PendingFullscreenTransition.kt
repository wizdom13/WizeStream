/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Completes explicit fullscreen intent independently of automatic rotation preferences. */
object PendingFullscreenTransition {
    data class ItemIdentity(val serviceId: Int, val url: String)

    enum class Result(val finished: Boolean) {
        NONE(false),
        WAITING(false),
        APPLIED(true),
        CANCELLED(true)
    }

    @JvmStatic
    @JvmOverloads
    fun identity(item: PlayQueueItem?, pendingIdentity: ItemIdentity? = null): ItemIdentity? = item?.let { ItemIdentity(it.serviceId, it.url) } ?: pendingIdentity

    @JvmStatic
    fun isStale(expected: ItemIdentity?, current: PlayQueueItem?): Boolean = expected != null && current != null && expected != identity(current)

    @JvmStatic
    fun canApply(player: Player?, nativePipPrepared: Boolean): Boolean = player != null && !nativePipPrepared && !player.isAudioOnly

    @JvmStatic
    fun shouldAlignToContent(ui: MainPlayerUi, targetState: Int): Boolean = targetState != FullscreenOrientationPolicy.EXIT_FULLSCREEN &&
        (targetState == FullscreenOrientationPolicy.ENTER_FULLSCREEN || ui.isFullscreen)

    @JvmStatic
    fun shouldPreservePortraitFullscreen(ui: MainPlayerUi, orientation: Int, requestedOrientation: Int, playerOwned: Boolean): Boolean = playerOwned &&
        ui.isFullscreen && orientation == Configuration.ORIENTATION_PORTRAIT && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    @JvmStatic
    fun apply(
        ui: MainPlayerUi?,
        orientation: Int,
        targetOrientation: Int,
        targetState: Int,
        expectedItem: ItemIdentity?,
        player: Player?,
        nativePipPrepared: Boolean
    ): Result {
        if (targetState == FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE) return Result.NONE
        if (isStale(expectedItem, player?.currentItem)) return Result.CANCELLED
        // Queue replacement can temporarily clear the active item before the next identity is
        // installed. Do not let that interval consume an intent belonging to the previous item.
        if (expectedItem != null && player?.currentItem == null) return Result.WAITING
        if (ui == null || !canApply(player, nativePipPrepared)) return Result.WAITING
        if (targetOrientation != Configuration.ORIENTATION_UNDEFINED && orientation != targetOrientation) return Result.WAITING
        val fullscreen = targetState == FullscreenOrientationPolicy.ENTER_FULLSCREEN
        // The setter can decline while its engine/listener is being attached. Keep the intent
        // until a later configuration, view-attachment or playback callback observes success.
        ui.setFullscreen(fullscreen)
        return if (ui.isFullscreen == fullscreen) Result.APPLIED else Result.WAITING
    }

    @JvmStatic
    fun shouldReleaseOrientation(playerOwned: Boolean, nonPhoneWindow: Boolean): Boolean = playerOwned || !nonPhoneWindow

    @JvmStatic
    fun releaseAbandonedOrientation(
        result: Result,
        ui: MainPlayerUi?,
        activity: Activity?,
        playerOwned: Boolean
    ): Boolean {
        if (result != Result.CANCELLED || !playerOwned || ui?.isFullscreen == true || activity == null) return playerOwned
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        return false
    }
}
