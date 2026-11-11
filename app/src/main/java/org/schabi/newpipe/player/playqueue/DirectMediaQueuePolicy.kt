package org.schabi.newpipe.player.playqueue

/** Live TV and radio use their own favorites, rather than extractor-backed video playlists. */
object DirectMediaQueuePolicy {
    @JvmStatic
    fun canSaveToLibrary(queue: PlayQueue?): Boolean = queue != null && queue.streams.none { it.isRadio || it.isLiveTv }
}
