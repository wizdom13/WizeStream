package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/** Background-only image loading with a shared budget for one notification worker run. */
internal class NotificationImages(
    private val context: Context,
    private val fetch: suspend (ImageRequest) -> Bitmap? = { context.imageLoader.execute(it).image?.toBitmap() },
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val perImageTimeoutMillis: Long = 2_000,
    private val budgetMillis: Long = 15_000
) {
    private var deadline: Long? = null

    fun load(url: String?, width: Int, height: Int): Bitmap? {
        if (url.isNullOrBlank() || !ImageStrategy.shouldLoadImages()) return null
        val started = now()
        val end = deadline ?: (started + budgetMillis).also { deadline = it }
        val timeout = minOf(perImageTimeoutMillis, end - started)
        if (timeout <= 0) return null
        return try {
            runBlocking {
                withTimeoutOrNull(timeout) {
                    val request = CoilHelper.getImageRequest(context, url, 0, false)
                        .size(width, height)
                        .scale(Scale.FIT)
                        .precision(Precision.EXACT)
                        .allowHardware(false)
                        // Notification bitmaps are short-lived; keep using the shared disk cache.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                    fetch(request)?.let { bitmap ->
                        val scale = minOf(1.0, width.toDouble() / bitmap.width, height.toDouble() / bitmap.height)
                        if (scale == 1.0) {
                            bitmap
                        } else {
                            Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * scale).toInt().coerceAtLeast(1),
                                (bitmap.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        }
                    }
                }
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("NotificationImages", "Unable to load notification artwork", error)
            null
        }
    }
}
