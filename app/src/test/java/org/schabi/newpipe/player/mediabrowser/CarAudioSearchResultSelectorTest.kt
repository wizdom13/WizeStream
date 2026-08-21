package org.schabi.newpipe.player.mediabrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class CarAudioSearchResultSelectorTest {
    @Test
    fun `selects the first stream and ignores non-playable search items`() {
        val channel = ChannelInfoItem(0, "https://example.com/channel", "Channel")
        val stream = StreamInfoItem(
            0,
            "https://example.com/watch?v=test",
            "Lesson",
            StreamType.VIDEO_STREAM
        )

        assertEquals(
            stream,
            CarAudioSearchResultSelector.firstPlayable(listOf<InfoItem>(channel, stream))
        )
    }

    @Test
    fun `returns null when search has no stream`() {
        val channel = ChannelInfoItem(0, "https://example.com/channel", "Channel")
        assertNull(CarAudioSearchResultSelector.firstPlayable(listOf(channel)))
    }
}
