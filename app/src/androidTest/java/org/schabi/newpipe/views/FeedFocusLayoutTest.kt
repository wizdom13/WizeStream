package org.schabi.newpipe.views

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class FeedFocusLayoutTest {
    @Test
    fun enteringFeedPrefersVideosButHeaderRemainsReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var root: FeedFocusLayout
            lateinit var header: Button
            lateinit var videos: RecyclerView
            var wasInTouchMode = true
            scenario.onActivity { activity ->
                wasInTouchMode = activity.window.decorView.isInTouchMode
                root = FeedFocusLayout(activity)
                header = Button(activity).apply {
                    text = "Tap for details"
                    isFocusable = true
                }
                root.addView(header, CoordinatorLayout.LayoutParams(300, 80))
                videos = RecyclerView(activity).apply {
                    id = R.id.items_list
                    layoutManager = LinearLayoutManager(activity)
                    adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = 2
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = object : RecyclerView.ViewHolder(
                            Button(parent.context).apply {
                                layoutParams = RecyclerView.LayoutParams(300, 80)
                                isFocusable = true
                            }
                        ) {}

                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                            (holder.itemView as Button).text = "Video $position"
                        }
                    }
                }
                root.addView(videos, CoordinatorLayout.LayoutParams(300, 300).apply { topMargin = 100 })
                activity.setContentView(root)
            }
            try {
                instrumentation.setInTouchMode(false)
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(videos.childCount > 0)
                    assertTrue("Header must accept focus before entering the feed", header.requestFocus())
                    assertTrue("Feed must accept keyboard focus", root.requestFocus(View.FOCUS_FORWARD))
                    assertTrue("Focus must land on a video row", videos.getChildAt(0).hasFocus())
                    assertTrue(header.requestFocus())
                    assertTrue(header.hasFocus())
                    videos.visibility = View.GONE
                    root.clearFocus()
                    assertTrue(root.requestFocus(View.FOCUS_FORWARD))
                    assertTrue(header.hasFocus())
                }
            } finally {
                instrumentation.setInTouchMode(wasInTouchMode)
            }
        }
    }
}
