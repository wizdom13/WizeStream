package org.schabi.newpipe.player.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33])
class PlayerControlHintsTest {
    @Test
    fun showsCurrentLabelInsidePlayerWithoutReplacingExistingActions() {
        val activity = Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(R.style.LightTheme)
        activity.setup().visible()
        try {
            val host = FrameLayout(activity.get())
            val captions = ImageButton(activity.get()).apply { contentDescription = "Captions" }
            val share = ImageButton(activity.get()).apply { contentDescription = "Share" }
            var clicks = 0
            var existingLongClicks = 0
            captions.setOnClickListener { clicks++ }
            share.setOnLongClickListener {
                existingLongClicks++
                true
            }
            host.addView(captions)
            host.addView(share)
            activity.get().setContentView(host)
            PlayerTooltips.disable(host)
            PlayerControlHints.install(host)

            assertTrue(captions.performLongClick())
            val hint = host.findViewById<TextView>(R.id.player_control_hint)
            assertEquals("Captions", hint.text.toString())
            assertEquals(View.VISIBLE, hint.visibility)
            captions.contentDescription = "Hide captions"
            captions.performLongClick()
            assertEquals("Hide captions", hint.text.toString())
            assertEquals(3, host.childCount)
            if (android.os.Build.VERSION.SDK_INT >= 26) assertNull(captions.tooltipText)
            captions.performClick()
            share.performLongClick()
            assertEquals(1, clicks)
            assertEquals(1, existingLongClicks)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertEquals(View.GONE, hint.visibility)
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
