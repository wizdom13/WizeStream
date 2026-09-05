/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.graphics.Bitmap
import android.util.Log
import coil3.Image
import coil3.request.Disposable
import coil3.target.Target
import coil3.toBitmap
import java.util.concurrent.Future
import org.schabi.newpipe.extractor.Image as ExtractorImage
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.media.LocalMediaThumbnailLoader
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.CoilHelper

/** Owns remote and local thumbnail loading, cancellation, and UI delivery. */
internal class PlayerThumbnailController(private val player: Player) {
    private var currentThumbnail: Bitmap? = null
    private var remoteLoad: Disposable? = null
    private var localLoad: Future<*>? = null

    fun load(thumbnails: List<ExtractorImage>) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "Thumbnail - load() called with thumbnails = [${thumbnails.size}]")
        }
        cancel()
        updateThumbnail(null)
        if (thumbnails.isEmpty()) {
            return
        }

        remoteLoad = CoilHelper.loadScaledDownThumbnail(
            player.context,
            thumbnails,
            object : Target {
                override fun onError(error: Image?) {
                    Log.e(Player.TAG, "Thumbnail - onError() called")
                    updateThumbnail(null)
                }

                override fun onStart(placeholder: Image?) {
                    if (Player.DEBUG) {
                        Log.d(Player.TAG, "Thumbnail - onStart() called")
                    }
                }

                override fun onSuccess(result: Image) {
                    if (Player.DEBUG) {
                        Log.d(Player.TAG, "Thumbnail - onSuccess() called with: image = [$result]")
                    }
                    updateThumbnail(result.toBitmap())
                }
            }
        )
    }

    fun loadLocal(item: PlayQueueItem) {
        cancel()
        updateThumbnail(null)
        localLoad = LocalMediaThumbnailLoader.loadBitmap(player.context, item) { bitmap ->
            val metadata = player.currentMetadata
            if (metadata is LocalMediaItemTag && metadata.item.isSameItem(item)) {
                val displayedBitmap =
                    if (bitmap != null || item.streamType != StreamType.AUDIO_STREAM) {
                        bitmap
                    } else {
                        LocalMediaThumbnailLoader.audioPlaceholderBitmap(player.context)
                    }
                updateThumbnail(displayedBitmap)
            }
        }
    }

    fun cancel() {
        remoteLoad?.dispose()
        remoteLoad = null
        localLoad?.cancel(true)
        localLoad = null
    }

    fun clear() {
        updateThumbnail(null)
    }

    fun getCurrentThumbnail(): Bitmap? = currentThumbnail

    private fun updateThumbnail(bitmap: Bitmap?) {
        if (currentThumbnail !== bitmap) {
            currentThumbnail = bitmap
            player.UIs().call { ui -> ui.onThumbnailLoaded(bitmap) }
        }
    }
}
