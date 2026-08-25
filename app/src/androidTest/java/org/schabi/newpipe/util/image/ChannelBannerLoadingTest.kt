package org.schabi.newpipe.util.image

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

@RunWith(AndroidJUnit4::class)
class ChannelBannerLoadingTest {
    @After
    fun restoreImagePreference() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun failedPreferredBannerFallsBackBeforeRevealingContainer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
        val views = inflateBannerViews(instrumentation, context)
        val invalidUrl = resourceUrl(context, 0)
        val validUrl = resourceUrl(context, R.drawable.ic_add)

        instrumentation.runOnMainSync {
            ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
            CoilHelper.loadBanner(
                views.banner,
                listOf(
                    Image(invalidUrl, 250, 1000, ResolutionLevel.MEDIUM),
                    Image(validUrl, 75, 300, ResolutionLevel.LOW)
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

    @Test
    fun failedBannerCandidatesLeaveNoReservedSpace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
        val views = inflateBannerViews(instrumentation, context)

        instrumentation.runOnMainSync {
            ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
            CoilHelper.loadBanner(
                views.banner,
                listOf(
                    Image(resourceUrl(context, 0), 250, 1000, ResolutionLevel.MEDIUM),
                    Image(resourceUrl(context, -1), 75, 300, ResolutionLevel.LOW)
                )
            )

            assertEquals(View.GONE, views.banner.visibility)
            assertEquals(View.GONE, views.container.visibility)
        }

        // Resource URI failures are local and immediate. Give both candidates time to complete and
        // verify that the loader never replaces the missing banner with a visible placeholder.
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals(View.GONE, views.banner.visibility)
            assertEquals(View.GONE, views.container.visibility)
            assertNull(views.banner.drawable)
        }
    }

    private fun inflateBannerViews(
        instrumentation: Instrumentation,
        context: Context
    ): BannerViews {
        lateinit var views: BannerViews
        instrumentation.runOnMainSync {
            val root = LayoutInflater.from(context)
                .inflate(R.layout.fragment_channel, FrameLayout(context), false)
            views = BannerViews(
                banner = root.findViewById(R.id.channel_banner_image),
                container = root.findViewById(R.id.channel_banner_container)
            )
        }
        return views
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

    private fun resourceUrl(context: Context, resourceId: Int): String {
        return "android.resource://${context.packageName}/$resourceId"
    }

    private data class BannerViews(
        val banner: ImageView,
        val container: View
    )
}
