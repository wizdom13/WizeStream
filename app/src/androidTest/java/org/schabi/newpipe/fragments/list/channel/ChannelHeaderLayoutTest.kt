package org.schabi.newpipe.fragments.list.channel

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class ChannelHeaderLayoutTest {
    @Test
    fun metadataRowIsMeasuredBelowBanner() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val root = LayoutInflater.from(context)
                .inflate(R.layout.fragment_channel, FrameLayout(context), false)

            val width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val height = View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
            root.measure(width, height)
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)

            val metadata = root.findViewById<View>(R.id.channel_metadata)
            val banner = root.findViewById<View>(R.id.channel_banner_image)
            val avatar = root.findViewById<View>(R.id.channel_avatar_view)
            val subscribeButton = root.findViewById<View>(R.id.channel_subscribe_button)

            assertTrue(avatar.top >= banner.bottom)
            assertTrue(metadata.height > banner.height)
            assertTrue(subscribeButton.top >= banner.bottom)
            assertTrue(subscribeButton.bottom <= metadata.height)
        }
    }
}
