package org.schabi.newpipe.util.image

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun failedPreferredBannerFallsBackBeforeRevealingContainer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val validBanner = createTestBanner(context)
        val missingBanner = File(context.cacheDir, "missing-banner-${System.nanoTime()}.png")

        try {
            withAttachedBannerViews(instrumentation) { views ->
                instrumentation.runOnMainSync {
                    CoilHelper.loadBanner(
                        views.banner,
                        listOf(
                            Image(
                                missingBanner.toURI().toString(),
                                250,
                                1000,
                                ResolutionLevel.MEDIUM
                            ),
                            Image(
                                validBanner.toURI().toString(),
                                75,
                                300,
                                ResolutionLevel.LOW
                            )
                        )
                    )

                    assertEquals(View.GONE, views.banner.visibility)
                    assertEquals(View.GONE, views.container.visibility)
                }

                waitForVisibility(instrumentation, views.container, View.VISIBLE)
                instrumentation.runOnMainSync {
                    assertEquals(View.VISIBLE, views.banner.visibility)
                    assertNotNull(views.banner.drawable)
                }
            }
        } finally {
            validBanner.delete()
        }
    }

    @Test
    fun failedBannerCandidatesLeaveNoReservedSpace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val firstMissing = File(context.cacheDir, "missing-banner-a-${System.nanoTime()}.png")
        val secondMissing = File(context.cacheDir, "missing-banner-b-${System.nanoTime()}.png")

        withAttachedBannerViews(instrumentation) { views ->
            instrumentation.runOnMainSync {
                CoilHelper.loadBanner(
                    views.banner,
                    listOf(
                        Image(
                            firstMissing.toURI().toString(),
                            250,
                            1000,
                            ResolutionLevel.MEDIUM
                        ),
                        Image(
                            secondMissing.toURI().toString(),
                            75,
                            300,
                            ResolutionLevel.LOW
                        )
                    )
                )

                assertEquals(View.GONE, views.banner.visibility)
                assertEquals(View.GONE, views.container.visibility)
            }

            // Local file failures are immediate. Give both candidates time to complete and verify
            // that the loader never replaces the missing banner with a visible placeholder.
            SystemClock.sleep(500)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(View.GONE, views.banner.visibility)
                assertEquals(View.GONE, views.container.visibility)
                assertNull(views.banner.drawable)
            }
        }
    }

    @Test
    fun clearingActiveBannerPreventsLateRequestFromRevealingIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val validBanner = createTestBanner(context)

        try {
            withAttachedBannerViews(instrumentation) { views ->
                instrumentation.runOnMainSync {
                    CoilHelper.loadBanner(
                        views.banner,
                        listOf(
                            Image(
                                validBanner.toURI().toString(),
                                250,
                                1000,
                                ResolutionLevel.MEDIUM
                            )
                        )
                    )
                    CoilHelper.clearBanner(views.banner)
                }

                SystemClock.sleep(500)
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
            instrumentation.runOnMainSync {
                assertTrue(views.banner.isAttachedToWindow)
            }
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

    private fun waitForVisibility(
        instrumentation: Instrumentation,
        view: View,
        expectedVisibility: Int,
        timeoutMillis: Long = 3_000
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var visibility = View.GONE
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                visibility = view.visibility
            }
            if (visibility == expectedVisibility) {
                return
            }
            SystemClock.sleep(25)
        }
        assertEquals(expectedVisibility, visibility)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private data class BannerViews(
        val banner: ImageView,
        val container: View
    )
}
