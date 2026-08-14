package org.schabi.newpipe.fragments.list.channel

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class ChannelHeaderLayoutTest {
    @Test
    fun headerControlsStayVisibleAcrossOrientationsAndBannerStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)

            assertHeaderAtSize(context, 1080, 2400, showBanner = true)
            assertHeaderAtSize(context, 1080, 2400, showBanner = false)
            assertHeaderAtSize(context, 2400, 1080, showBanner = true)
            assertHeaderAtSize(context, 2400, 1080, showBanner = false)
        }
    }

    private fun assertHeaderAtSize(
        context: Context,
        widthPixels: Int,
        heightPixels: Int,
        showBanner: Boolean
    ) {
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel, FrameLayout(context), false)
        val banner = root.findViewById<ImageView>(R.id.channel_banner_image)
        if (!showBanner) {
            banner.setImageDrawable(null)
        }

        val width = View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY)
        root.measure(width, height)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val metadata = root.findViewById<View>(R.id.channel_metadata)
        val metadataRow = root.findViewById<View>(R.id.channel_metadata_row)
        val avatar = root.findViewById<View>(R.id.channel_avatar_view)
        val title = root.findViewById<View>(R.id.channel_title_view)
        val subscriberCount = root.findViewById<View>(R.id.channel_subscriber_view)
        val subscribeButton = root.findViewById<View>(R.id.channel_subscribe_button)

        assertTrue(metadataRow.top >= banner.bottom)
        assertTrue(metadataRow.bottom <= metadata.height)

        listOf(avatar, title, subscriberCount, subscribeButton).forEach {
            assertVisibleAndMeasured(it)
            assertContainedIn(metadataRow, it)
        }
    }

    private fun assertVisibleAndMeasured(view: View) {
        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.width > 0)
        assertTrue(view.height > 0)
    }

    private fun assertContainedIn(container: View, child: View) {
        assertTrue(child.left >= 0)
        assertTrue(child.top >= 0)
        assertTrue(child.right <= container.width)
        assertTrue(child.bottom <= container.height)
    }
}
