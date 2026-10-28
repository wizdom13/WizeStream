package org.schabi.newpipe.player.ui

import android.view.View
import org.schabi.newpipe.databinding.PlayerBinding

object ArchivePresentation {
    @JvmStatic
    fun isEnded(engine: androidx.media3.common.Player?): Boolean {
        val timeline = engine?.currentTimeline ?: return false
        if (timeline.isEmpty) return false
        val index = engine.currentMediaItemIndex
        if (index !in 0 until timeline.windowCount) return false
        val window = timeline.getWindow(index, androidx.media3.common.Timeline.Window())
        return !window.isPlaceholder && !window.isDynamic && window.durationUs > 0
    }

    @JvmStatic
    fun onTimelineChanged(player: org.schabi.newpipe.player.Player, binding: PlayerBinding) {
        val info = player.currentStreamInfo.orElse(null) ?: return
        val engine = player.exoPlayer ?: return
        val item = engine.currentMediaItem ?: return
        val tag = org.schabi.newpipe.player.mediaitem.MediaItemTag.from(item).orElse(null)
        if (info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM &&
            tag?.streamUrl == info.url && player.playbackPresentationMode.rendersVideo() && isEnded(engine)
        ) {
            show(binding)
        }
    }

    @JvmStatic
    fun show(binding: PlayerBinding) {
        // Manifest-backed archives have no extractor quality list, but still have a video
        // surface and a finite Media3 timeline. Do not hide those with the quality selector.
        binding.surfaceView.visibility = View.VISIBLE
        binding.endScreen.visibility = View.GONE
        binding.playbackEndTime.visibility = View.VISIBLE
        binding.playbackLiveSync.visibility = View.GONE
    }
}
