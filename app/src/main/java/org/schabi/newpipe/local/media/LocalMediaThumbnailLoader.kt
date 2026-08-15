/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import androidx.annotation.WorkerThread
import coil3.util.CoilUtils
import java.util.concurrent.Executors
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/** Loads device-local artwork without treating a video content URI as an online image URL. */
object LocalMediaThumbnailLoader {
    private const val THUMBNAIL_WIDTH = 512
    private const val THUMBNAIL_HEIGHT = 288
    private const val CACHE_SIZE_BYTES = 16 * 1024 * 1024

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun load(target: ImageView, item: LocalMediaItem) {
        load(target, item.contentUri, item.mediaStoreId, item.isVideo, item.thumbnailUri)
    }

    fun load(target: ImageView, item: PlayQueueItem) {
        load(
            target,
            item.url,
            item.localMediaId,
            item.streamType == org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM,
            item.localThumbnailUrl
        )
    }

    private fun load(
        target: ImageView,
        contentUri: String,
        mediaStoreId: Long,
        isVideo: Boolean,
        thumbnailUri: String?
    ) {
        clear(target)
        val requestKey = contentUri
        target.tag = requestKey

        if (!ImageStrategy.shouldLoadImages()) return
        if (!isVideo) {
            CoilHelper.loadThumbnail(target, thumbnailUri)
            return
        }

        cache[requestKey]?.let {
            target.setImageBitmap(it)
            return
        }

        val resolver = target.context.applicationContext.contentResolver
        executor.execute {
            val bitmap = loadVideoThumbnail(resolver, contentUri, mediaStoreId)
            if (bitmap != null) cache.put(requestKey, bitmap)
            mainHandler.post {
                if (target.tag == requestKey && bitmap != null) target.setImageBitmap(bitmap)
            }
        }
    }

    fun clear(target: ImageView) {
        target.tag = null
        CoilUtils.dispose(target)
        target.setImageResource(R.drawable.placeholder_thumbnail_video)
    }

    @WorkerThread
    private fun loadVideoThumbnail(
        resolver: ContentResolver,
        contentUri: String,
        mediaStoreId: Long
    ): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.loadThumbnail(
                    android.net.Uri.parse(contentUri),
                    Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    resolver,
                    mediaStoreId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
