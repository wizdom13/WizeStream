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
    fun builtInCountryPickerSearchesWorldwideAndDefaultsToIptvOrgWithoutNetworkRequests() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val dialogs = mutableListOf<androidx.appcompat.app.AlertDialog>()
            var selected: TvDirectorySelection? = null
            try {
                scenario.onActivity { activity ->
                    assertTrue(TvDirectory.choices(activity, TvDirectorySource.IPTV_ORG).size > 150)
                    assertTrue(TvDirectory.choices(activity, TvDirectorySource.FREE_TV).size > 70)
                    TvDirectoryPicker(activity.layoutInflater, { dialogs.add(it) }).show(null) { selected = it }
                    assertEquals(0, dialogs.last().listView.checkedItemPosition)
                    dialogs.last().getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
                }
                // AlertDialog posts button callbacks; wait for that transition before searching.
                scenario.onActivity { activity ->
                    val country = dialogs.last()
                    country.findViewById<android.widget.EditText>(R.id.radio_dialog_edit)!!.setText("Lebanon")
                    val list = country.findViewById<android.widget.ListView>(R.id.radio_filter_choices)!!
                    assertEquals(1, list.count)
                    list.performItemClick(View(activity), 0, 0)
                    assertEquals("LB", selected?.country?.code)
                    assertEquals(TvDirectorySource.IPTV_ORG, selected?.source)
                    assertEquals("https://iptv-org.github.io/iptv/countries/lb.m3u", selected?.address)
                }
            } finally {
                scenario.onActivity { dialogs.forEach { dialog -> dialog.dismiss() } }
            }
        }
    }

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
                assertTrue(root.findViewById<View>(R.id.tv_browse) is com.google.android.material.chip.Chip)
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
