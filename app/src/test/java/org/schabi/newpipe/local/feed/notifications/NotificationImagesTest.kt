package org.schabi.newpipe.local.feed.notifications

import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationImagesTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun restoreImages() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun artworkIsSoftwareDecodedAndBoundedWithoutChangingAspectRatio() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.HIGH)
        lateinit var request: ImageRequest
        val loader = NotificationImages(context, fetch = {
            request = it
            Bitmap.createBitmap(1280, 640, Bitmap.Config.ARGB_8888)
        })
        val image = requireNotNull(loader.load("https://example.com/image.jpg", 640, 360))
        assertEquals(640, image.width)
        assertEquals(320, image.height)
        assertFalse(request.allowHardware)
        assertEquals(Size(640, 360), runBlocking { request.sizeResolver.size() })
    }

    @Test
    fun missingDisabledAndFailedImagesFallBackWithoutEscapingErrors() {
        var calls = 0
        val loader = NotificationImages(context, fetch = {
            calls++
            throw IOException("offline")
        })
        assertNull(loader.load(null, 640, 360))
        assertNull(loader.load(" ", 640, 360))
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.NONE)
        assertNull(loader.load("https://example.com/image.jpg", 640, 360))
        assertEquals(0, calls)
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        assertNull(loader.load("https://example.com/image.jpg", 640, 360))
        assertEquals(1, calls)
    }

    @Test
    fun slowImagesAreCancelledAndTheBatchBudgetSkipsFurtherRequests() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        val now = AtomicLong(0)
        var calls = 0
        val loader = NotificationImages(context, fetch = {
            calls++
            delay(1_000)
            null
        }, now = now::get, perImageTimeoutMillis = 20, budgetMillis = 50)
        assertNull(loader.load("https://example.com/slow.jpg", 640, 360))
        assertEquals(1, calls)
        now.set(50)
        assertNull(loader.load("https://example.com/next.jpg", 640, 360))
        assertEquals(1, calls)
    }
}
