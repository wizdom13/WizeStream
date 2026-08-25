package org.schabi.newpipe.util.image

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

@RunWith(AndroidJUnit4::class)
class ChannelBannerLoadingTest {
    @Before
    fun enableImages() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @After
    fun restoreImagePreference() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun successfulBannerRequestKeepsHeaderVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val validBanner = createTestBanner(context)

        try {
            withAttachedBannerViews(instrumentation) { views ->
                instrumentation.runOnMainSync {
                    ImageViewCompat.setImageTintList(
                        views.banner,
                        ColorStateList.valueOf(Color.MAGENTA)
                    )
                    views.banner.setColorFilter(Color.CYAN)
                    CoilHelper.loadBanner(views.banner, listOf(testImage(validBanner)))
                    assertNull(ImageViewCompat.getImageTintList(views.banner))
                    assertNull(views.banner.colorFilter)
                }

                waitForDrawable(instrumentation, views.banner)
                instrumentation.runOnMainSync {
                    assertEquals(View.VISIBLE, views.banner.visibility)
                    assertEquals(View.VISIBLE, views.container.visibility)
                    assertNotNull(views.banner.drawable)
                }
            }
        } finally {
            validBanner.delete()
        }
    }

    @Test
    fun clearBannerDisposesImageAndCollapsesHeader() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val validBanner = createTestBanner(context)

        try {
            withAttachedBannerViews(instrumentation) { views ->
                instrumentation.runOnMainSync {
                    CoilHelper.loadBanner(views.banner, listOf(testImage(validBanner)))
                    CoilHelper.clearBanner(views.banner)
                }

                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(View.GONE, views.banner.visibility)
                    assertEquals(View.GONE, views.container.visibility)
                    assertNull(views.banner.drawable)
                }
            }
        } finally {
            validBanner.delete()
        }
    }

    private fun testImage(file: File) = Image(
        file.toURI().toString(),
        250,
        1000,
        ResolutionLevel.MEDIUM
    )

    private fun withAttachedBannerViews(
        instrumentation: Instrumentation,
        block: (BannerViews) -> Unit
    ) {
        val targetContext = instrumentation.targetContext
        val intent = Intent(targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as MainActivity

        try {
            lateinit var views: BannerViews
            instrumentation.runOnMainSync {
                val height = dpToPx(activity, 70)
                val container = FrameLayout(activity)
                val banner = ImageView(activity).apply {
                    maxHeight = height
                }
                container.addView(
                    banner,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
                )
                activity.setContentView(container)
                views = BannerViews(banner, container)
            }
            instrumentation.waitForIdleSync()
            block(views)
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }

    private fun createTestBanner(context: Context): File {
        val file = File(context.cacheDir, "channel-banner-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        return file
    }

    private fun waitForDrawable(
        instrumentation: Instrumentation,
        view: ImageView,
        timeoutMillis: Long = 3_000
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var hasDrawable = false
            instrumentation.runOnMainSync {
                hasDrawable = view.drawable != null
            }
            if (hasDrawable) {
                return
            }
            SystemClock.sleep(25)
        }
        instrumentation.runOnMainSync {
            assertNotNull(view.drawable)
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private data class BannerViews(
        val banner: ImageView,
        val container: View
    )
}
