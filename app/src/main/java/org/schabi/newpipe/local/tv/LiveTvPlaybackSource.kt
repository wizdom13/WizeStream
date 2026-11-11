package org.schabi.newpipe.local.tv

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.IOException
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

internal object LiveTvPlaybackSource {
    fun create(item: PlayQueueItem): MediaSource? {
        if (!item.isLiveTv || tvUrl(item.url) == null) return null
        val http = DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT)
        val https = ResolvingDataSource.Factory(http) { request ->
            if (tvUrl(request.uri.toString()) == null) throw IOException("Live TV requires HTTPS")
            request
        }
        val media = LocalMediaItemTag.of(item).asMediaItem().buildUpon().setMimeType(item.mimeType).build()
        return DefaultMediaSourceFactory(https).createMediaSource(media)
    }
}
