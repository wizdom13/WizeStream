/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Finishes accelerated DVR playback at the safe live position without saving a speed preference. */
internal class LiveCatchupController(private val player: Player) {
    private var currentItem: PlayQueueItem? = null
    private var completedItem: PlayQueueItem? = null

    fun setItem(item: PlayQueueItem?) {
        if (currentItem !== item) {
            currentItem = item
            completedItem = null
        }
    }

    fun clear() {
        currentItem = null
        completedItem = null
    }

    /** An explicit speed choice can start another catch-up after the user rewinds. */
    fun onParametersRequested() {
        completedItem = null
    }

    fun profileSpeed(item: PlayQueueItem?, preferredSpeed: Float): Float {
        setItem(item)
        // Metadata can mislabel a live source as ordinary video. Only a matching, fully
        // described static window proves that this item is no longer active live playback.
        // Engine/source rebuilds temporarily expose empty or placeholder timelines; those
        // must not discard a completed catch-up or restore its accelerated speed.
        val window = player.getExoPlayer()?.let(::matchingWindow)
        if (window?.isDynamic == false) {
            completedItem = null
        }
        // Metadata/source reloads must not restart acceleration after this session caught up.
        return if (item != null && completedItem === item && preferredSpeed > 1f) 1f else preferredSpeed
    }

    fun update() {
        val engine = player.getExoPlayer() ?: return
        val window = activeWindow(engine) ?: return
        if (!engine.playWhenReady || engine.isPlayingAd ||
            engine.playbackState != Media3Player.STATE_READY &&
            engine.playbackState != Media3Player.STATE_BUFFERING
        ) {
            return
        }

        val speed = engine.playbackParameters.speed
        if (!speed.isFinite() || speed <= 1f) return
        val position = engine.currentPosition
        if (position < 0 || window.defaultPositionMs - position > LIVE_POSITION_TOLERANCE_MS) return

        complete(engine)
        // Seeking in a live window sets a target-offset override inside Media3. Merely setting
        // speed to 1x would reactivate its controller with that old, rewound target. A default
        // seek clears the override and rejoins the source's safe live position, retaining DVR.
        engine.seekToDefaultPosition()
    }

    /** A deliberate Go live action and behind-window recovery also finish accelerated playback. */
    fun beforeDefaultSeek() {
        val engine = player.getExoPlayer() ?: return
        if (activeWindow(engine) != null && engine.playbackParameters.speed > 1f) {
            complete(engine)
        }
    }

    private fun complete(engine: ExoPlayer) {
        // Set the latch before changing the engine: its listeners may run again as a result.
        completedItem = currentItem
        engine.playbackParameters = engine.playbackParameters.withSpeed(1f)
    }

    private fun activeWindow(engine: ExoPlayer): Timeline.Window? = matchingWindow(engine)?.takeIf {
        it.isLive && it.isDynamic && it.isSeekable
    }

    private fun matchingWindow(engine: ExoPlayer): Timeline.Window? {
        val item = currentItem ?: return null
        val playingItem = player.currentItem ?: player.playQueue?.item
        if (playingItem !== item) return null
        val tag = MediaItemTag.from(engine.currentMediaItem).orElse(null) ?: return null
        // Queue synchronization applies profiles before Media3 has necessarily switched items.
        if (tag.serviceId != item.serviceId || tag.streamUrl != item.url) return null
        val timeline = engine.currentTimeline
        val index = engine.currentMediaItemIndex
        if (timeline.isEmpty || index !in 0 until timeline.windowCount) return null
        val window = timeline.getWindow(index, Timeline.Window())
        if (window.isPlaceholder ||
            window.durationMs <= 0 || window.defaultPositionMs < 0 ||
            window.defaultPositionMs > window.durationMs
        ) {
            return null
        }
        return window
    }

    companion object {
        // Progress is sampled every second. Finish just before the safe default, never at the
        // end of the available media where an accelerated player can already be out of buffer.
        private const val LIVE_POSITION_TOLERANCE_MS = 1_000L
    }
}
