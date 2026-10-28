package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.stream.StreamType

object YoutubeStreamKind {
    @JvmStatic
    fun resolve(response: JsonObject, hasTokens: Boolean): StreamType = when {
        // YouTube can retain liveStreamability while the ended stream's DVR is processing.
        response.getObject("videoDetails").getBoolean("isPostLiveDvr", false) -> StreamType.POST_LIVE_STREAM

        response.getObject("playabilityStatus").has("liveStreamability") &&
            (hasTokens || response.getObject("streamingData").has("hlsManifestUrl")) -> StreamType.LIVE_STREAM

        else -> StreamType.VIDEO_STREAM
    }
}
