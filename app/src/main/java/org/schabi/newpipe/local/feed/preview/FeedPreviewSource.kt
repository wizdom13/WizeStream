package org.schabi.newpipe.local.feed.preview

import android.content.Context
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ListHelper

/** Reuses normal backend routing and host trust checks; never constructs raw media URLs. */
internal class FeedPreviewSource(private val context: Context) {
    fun load(stream: StreamEntity): Single<MediaSource> = ExtractorHelper.getStreamInfo(stream.serviceId, stream.url, false).map { info ->
        val quality = object : VideoPlaybackResolver.QualityResolver {
            override fun getDefaultResolutionIndex(sortedVideos: MutableList<VideoStream>) = ListHelper.getResolutionIndex(context, sortedVideos, "360p")
            override fun getOverrideResolutionIndex(sortedVideos: MutableList<VideoStream>, playbackQuality: String) = getDefaultResolutionIndex(sortedVideos)
            override fun getAutoFallbackResolutionIndex(sortedVideos: MutableList<VideoStream>) = getDefaultResolutionIndex(sortedVideos)
            override fun isDefaultAutoQuality() = false
            override fun isAutoQuality(playbackQuality: String) = false
        }
        val dataSource = PlayerDataSource(context, DefaultBandwidthMeter.Builder(context).build())
        VideoPlaybackResolver(context, dataSource, quality).resolve(info, "360p")
            ?: throw IllegalStateException("No playable preview")
    }
}
