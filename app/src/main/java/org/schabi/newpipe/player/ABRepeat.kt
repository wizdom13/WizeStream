package org.schabi.newpipe.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player as MediaPlayer
import androidx.media3.exoplayer.ExoPlayer

/** Session-only range. Manual seeks outside the range and queue transitions cancel it. */
class ABRepeat : MediaPlayer.Listener {
    private var engine: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    var startMs: Long? = null
        private set
    var endMs: Long? = null
        private set
    val active: Boolean get() = startMs != null && endMs != null
    private val tick = object : Runnable {
        override fun run() {
            val current = engine ?: return
            if (!active) return
            if (current.isPlaying && current.currentPosition >= requireNotNull(endMs)) {
                current.seekTo(requireNotNull(startMs))
            }
            handler.postDelayed(this, 50)
        }
    }

    fun attach(player: ExoPlayer) {
        detach()
        engine = player
        player.addListener(this)
    }

    fun detach() {
        clear()
        engine?.removeListener(this)
        engine = null
    }

    fun available(): Boolean = engine?.let {
        it.isCurrentMediaItemSeekable && !it.isCurrentMediaItemLive && it.duration > 500
    } == true

    fun markStart(): Boolean {
        val current = engine ?: return false
        if (!available()) return false
        clear()
        startMs = current.currentPosition
        return true
    }

    fun markEnd(): Boolean {
        val current = engine ?: return false
        val start = startMs ?: return false
        val end = current.currentPosition.coerceAtMost(current.duration - 100)
        if (!available() || !validRange(start, end)) return false
        endMs = end
        current.seekTo(start)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return true
    }

    fun clear() {
        handler.removeCallbacks(tick)
        startMs = null
        endMs = null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = clear()

    override fun onPositionDiscontinuity(oldPosition: MediaPlayer.PositionInfo, newPosition: MediaPlayer.PositionInfo, reason: Int) {
        if (active && reason == MediaPlayer.DISCONTINUITY_REASON_SEEK &&
            (newPosition.positionMs < requireNotNull(startMs) || newPosition.positionMs > requireNotNull(endMs))
        ) {
            clear()
        }
    }

    companion object {
        internal fun validRange(start: Long, end: Long): Boolean = start >= 0 && end - start >= 500
    }
}
