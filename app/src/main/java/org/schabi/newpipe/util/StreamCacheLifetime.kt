package org.schabi.newpipe.util

import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

object StreamCacheLifetime {
    @JvmStatic
    fun expiration(info: Info, normal: Long): Long = if (info is StreamInfo && info.streamType in setOf(
            StreamType.LIVE_STREAM,
            StreamType.AUDIO_LIVE_STREAM,
            StreamType.POST_LIVE_STREAM,
            StreamType.POST_LIVE_AUDIO_STREAM
        )
    ) {
        minOf(normal, 30_000L)
    } else {
        normal
    }
}
