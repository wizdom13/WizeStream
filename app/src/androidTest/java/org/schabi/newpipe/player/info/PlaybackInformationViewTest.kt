package org.schabi.newpipe.player.info

import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.about.AboutActivity

@RunWith(AndroidJUnit4::class)
class PlaybackInformationViewTest {
    @Test
    fun panelRefreshesOnlyWhileAttachedAndVisible() {
        val ticks = AtomicInteger()
        val nextTicks = AtomicReference(CountDownLatch(2))
        lateinit var panel: PlaybackInformationView
        lateinit var root: FrameLayout
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                root = FrameLayout(activity)
                panel = PlaybackInformationView(activity).apply { visibility = View.GONE }
                root.addView(panel)
                activity.setContentView(root)
                val details = (panel.getChildAt(1) as ScrollView).getChildAt(0) as TextView
                details.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        ticks.incrementAndGet()
                        nextTicks.get().countDown()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                panel.bind(PlaybackInformation())
            }
            SystemClock.sleep(1200)
            assertEquals(0, ticks.get())
            scenario.onActivity { panel.toggle() }
            assertTrue("Visible panel should refresh repeatedly", nextTicks.get().await(5, TimeUnit.SECONDS))

            scenario.onActivity {
                val header = panel.getChildAt(0) as LinearLayout
                (header.getChildAt(1) as ImageButton).performClick()
                assertEquals(View.GONE, panel.visibility)
            }
            val closedTicks = ticks.get()
            SystemClock.sleep(1200)
            assertEquals(closedTicks, ticks.get())

            nextTicks.set(CountDownLatch(1))
            scenario.onActivity { panel.toggle() }
            assertTrue(nextTicks.get().await(5, TimeUnit.SECONDS))
            scenario.moveToState(Lifecycle.State.CREATED)
            val backgroundTicks = ticks.get()
            SystemClock.sleep(1200)
            assertEquals(backgroundTicks, ticks.get())
            nextTicks.set(CountDownLatch(1))
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("Returning to the window should resume refreshes", nextTicks.get().await(5, TimeUnit.SECONDS))

            scenario.onActivity { root.removeView(panel) }
            val detachedTicks = ticks.get()
            SystemClock.sleep(1200)
            assertEquals(detachedTicks, ticks.get())
            scenario.onActivity { panel.clear() }
        }
    }
}
