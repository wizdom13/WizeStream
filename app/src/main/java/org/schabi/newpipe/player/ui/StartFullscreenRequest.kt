package org.schabi.newpipe.player.ui

import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Keeps an explicit startup request until the main player can actually enter fullscreen. */
class StartFullscreenRequest {
    private var pendingItem: PlayQueueItem? = null

    fun request(item: PlayQueueItem?) {
        pendingItem = item
    }

    fun apply(ui: MainPlayerUi, alignOrientation: Runnable) {
        val requested = pendingItem ?: return
        val current = ui.getPlayer().currentItem ?: return
        if (requested.serviceId != current.serviceId || requested.url != current.url) return
        // Refresh the content identity before using dimensions retained by a reused player.
        ui.checkLandscape()
        ui.setFullscreen(true)
        if (!ui.isFullscreen) return
        pendingItem = null
        // Unknown dimensions must not force a portrait video into landscape. MainPlayerUi's
        // first valid onVideoSizeChanged callback aligns an already-fullscreen player.
        if (ui.videoContentOrientation != FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN) {
            alignOrientation.run()
        }
    }
}
