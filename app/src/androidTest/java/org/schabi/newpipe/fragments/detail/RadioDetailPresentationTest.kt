package org.schabi.newpipe.fragments.detail

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(AndroidJUnit4::class)
class RadioDetailPresentationTest {
    @Test
    fun radioReplacesOldVideoLabelsAndHidesVideoSpecificContent() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = FragmentVideoDetailBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                binding.detailVideoTitleView.text = "Previous video"
                binding.detailSubChannelTextView.text = "Previous channel"
                binding.viewPager.visibility = View.VISIBLE
                binding.detailControlsDownload.visibility = View.VISIBLE
                val station = PlayQueueItem.radioStation("Station", "https://example.com/live", "Country · Language", false, null)

                RadioDetailPresentation.show(binding, station)

                assertEquals("Station", binding.detailVideoTitleView.text.toString())
                assertEquals("Country · Language", binding.detailSubChannelTextView.text.toString())
                assertEquals(View.GONE, binding.viewPager.visibility)
                assertEquals(View.GONE, binding.detailControlsDownload.visibility)
                assertEquals(View.GONE, binding.detailControlsPopup.visibility)
                assertEquals(View.GONE, binding.detailDurationView.visibility)
                assertEquals(View.GONE, binding.detailThumbnailPlayButton.visibility)
            }
        }
    }
}
