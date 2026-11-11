package org.schabi.newpipe.local.tv

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.fragments.detail.LiveTvDetailPresentation
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class LiveTvLayoutTest {
    @Test
    fun materialScreenAndChannelRowsInflateWithRemoteFocusableItems() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.layoutInflater.inflate(R.layout.fragment_live_tv, null)
                activity.setContentView(root)
                val list = root.findViewById<RecyclerView>(R.id.tv_stations)
                val adapter = TvChannelAdapter({}, {})
                val holder = adapter.onCreateViewHolder(list, 0)
                assertTrue(holder.itemView.isFocusable)
                assertTrue(root.findViewById<View>(R.id.tv_add) is com.google.android.material.chip.Chip)
                org.junit.Assert.assertFalse(root.findViewById<View>(R.id.tv_add).isEnabled)
                val header = root.findViewById<ViewGroup>(R.id.tv_header)
                val flags = (header.getChildAt(0).layoutParams as com.google.android.material.appbar.AppBarLayout.LayoutParams).scrollFlags
                assertTrue(flags and com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS != 0)
            }
        }
    }

    @Test
    fun directTvReplacesPreviousVideoDetailsAndKeepsTheVideoPlayButton() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = FragmentVideoDetailBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                binding.detailVideoTitleView.text = "Old video"
                binding.detailSubChannelTextView.text = "Old channel"
                val channel = PlayQueueItem.liveTv("News", "https://tv.example/live.m3u8", "World", "application/x-mpegURL", null)
                LiveTvDetailPresentation.show(binding, channel)
                assertEquals("News", binding.detailVideoTitleView.text.toString())
                assertEquals("World", binding.detailSubChannelTextView.text.toString())
                assertEquals(activity.getString(R.string.live_tv), binding.detailUploaderTextView.text.toString())
                assertEquals(View.VISIBLE, binding.detailThumbnailPlayButton.visibility)
                assertEquals(View.GONE, binding.detailControlsDownload.visibility)
                assertEquals(View.GONE, binding.viewPager.visibility)
            }
        }
    }
}
