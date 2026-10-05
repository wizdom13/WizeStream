package org.schabi.newpipe.player

import androidx.media3.common.Player as Media3Player
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.ChannelSkipPreferences
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Skips configured boundaries once while leaving manual seeks and queue completion to Media3. */
internal class ChannelSkipPlaybackController(private val player: Player) {
    private var item: PlayQueueItem? = null
    private var introPending = false
    private var manualOverride = false
    private var endProcessed = false
    private var configured = false
    private var startMillis = 0L
    private var endMillis = 0L

    fun begin(newItem: PlayQueueItem, restoringPosition: Boolean) {
        item = newItem
        introPending = !restoringPosition
        manualOverride = false
        endProcessed = false
        configured = false
    }

    fun manualSeek() {
        if (item === player.currentItem) {
            introPending = false
            manualOverride = true
        }
    }

    fun automaticTransition() {
        introPending = true
        manualOverride = false
        endProcessed = false
        configured = false
    }

    fun onProgress() {
        if (player.exoPlayerIsNull() || !player.isPlaying || endProcessed) return
        val currentItem = player.currentItem ?: return
        val info = player.currentStreamInfo.orElse(null) ?: return
        val engine = player.exoPlayer
        if (
            item !== currentItem || info.url != currentItem.url ||
            info.serviceId != currentItem.serviceId || engine.isCurrentMediaItemDynamic ||
            info.streamType != StreamType.VIDEO_STREAM ||
            engine.currentMediaItemIndex != player.playQueue?.index ||
            engine.duration <= 0 || engine.playbackState != Media3Player.STATE_READY
        ) {
            return
        }
        if (!configured) {
            startMillis = ChannelSkipPreferences.startSeconds(player.context, info) * 1000L
            endMillis = ChannelSkipPreferences.endSeconds(player.context, info) * 1000L
            introPending = introPending && info.startPosition <= 0
            configured = true
        }
        val action = ChannelSkipPolicy.decide(
            engine.duration,
            engine.currentPosition,
            startMillis,
            endMillis,
            introPending,
            manualOverride
        )
        introPending = false
        when (action) {
            ChannelSkipPolicy.Action.INTRO -> engine.seekTo(startMillis)

            ChannelSkipPolicy.Action.OUTRO -> {
                endProcessed = true
                player.saveStreamProgressStateCompleted()
                engine.seekTo(engine.duration)
            }

            ChannelSkipPolicy.Action.NONE -> Unit
        }
    }
}
