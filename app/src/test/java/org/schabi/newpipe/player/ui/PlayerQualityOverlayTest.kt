package org.schabi.newpipe.player.ui

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlayerQualityOverlayTest {
    @Test fun feedbackIsPlayerBoundAndClearedWhenMinimizedOrExpired() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            val label = TextView(activity).apply {
                id = R.id.playerQualityFeedback
                visibility = View.GONE
            }
            root.addView(View(activity).apply { id = R.id.surfaceView })
            root.addView(label)
            activity.setContentView(root)
            val density = activity.resources.displayMetrics.density
            root.layout(0, 0, (400 * density).toInt(), (250 * density).toInt())
            val overlay = PlayerQualityOverlay()
            overlay.show(root, "720p → 480p")
            assertEquals(View.VISIBLE, label.visibility)
            assertEquals("720p → 480p", label.text.toString())
            root.layout(0, 0, (160 * density).toInt(), (90 * density).toInt())
            assertEquals(View.GONE, label.visibility)
            overlay.show(root, "480p → 360p")
            assertEquals(View.GONE, label.visibility)
            root.layout(0, 0, (400 * density).toInt(), (250 * density).toInt())
            overlay.show(root, "360p → 480p")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertEquals(View.GONE, label.visibility)
            overlay.show(root, "480p → 720p")
            root.removeView(root.findViewById(R.id.surfaceView))
            root.viewTreeObserver.dispatchOnGlobalLayout()
            assertEquals(View.GONE, label.visibility)
            overlay.show(root, "720p → 480p")
            assertEquals(View.GONE, label.visibility)
            overlay.clear()
        }
    }
}
