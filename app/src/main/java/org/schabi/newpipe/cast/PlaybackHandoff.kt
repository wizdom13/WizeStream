package org.schabi.newpipe.cast

internal class PlaybackHandoff(
    private val resumePositionProvider: () -> Double,
    private val remotePlaybackStarted: () -> Unit
) {
    private var pending = true

    @Synchronized
    fun resumePositionSeconds(): Double {
        if (!pending) return 0.0

        return resumePositionProvider()
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
    }

    fun confirmRemotePlayback() {
        val callback = synchronized(this) {
            if (!pending) return
            pending = false
            remotePlaybackStarted
        }
        callback()
    }

    @Synchronized
    fun cancel() {
        pending = false
    }
}
