package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType

class YoutubeStreamKindTest {
    @Test fun postLiveDvrWinsOverLeftoverLiveControls() {
        val response = JsonParser.`object`().from("""{"videoDetails":{"isPostLiveDvr":true},"playabilityStatus":{"liveStreamability":{}},"streamingData":{"hlsManifestUrl":"https://example.com/archive.m3u8"}}""")
        assertEquals(StreamType.POST_LIVE_STREAM, YoutubeStreamKind.resolve(response, false))
        assertEquals(StreamType.POST_LIVE_STREAM, YoutubeStreamKind.resolve(response, true))
        response.getObject("videoDetails")["isPostLiveDvr"] = false
        assertEquals(StreamType.LIVE_STREAM, YoutubeStreamKind.resolve(response, false))
        response.getObject("playabilityStatus").remove("liveStreamability")
        assertEquals(StreamType.VIDEO_STREAM, YoutubeStreamKind.resolve(response, false))
    }
}
