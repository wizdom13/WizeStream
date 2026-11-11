package org.schabi.newpipe.fragments.detail

import android.view.View
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Direct live channels have no extractor comments, downloads, or related-video pages. */
internal object LiveTvDetailPresentation {
    @JvmStatic
    fun show(binding: FragmentVideoDetailBinding, channel: PlayQueueItem) {
        RadioDetailPresentation.show(binding, channel)
        org.schabi.newpipe.util.image.CoilHelper.loadThumbnail(binding.detailThumbnailImageView, channel.localThumbnailUrl)
        binding.detailUploaderTextView.setText(R.string.live_tv)
        binding.detailSubChannelTextView.text = channel.uploader
        binding.detailSubChannelTextView.visibility = if (channel.uploader.isBlank()) View.GONE else View.VISIBLE
        binding.detailThumbnailPlayButton.visibility = View.VISIBLE
        binding.detailThumbnailPlayButton.setImageResource(R.drawable.ic_play_arrow_shadow)
    }
}
