package org.schabi.newpipe.extractor.channel

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelHandleTest {
    @Test
    fun readsVanityUrlAndDecodesUnicode() {
        val response = JsonParser.`object`().from("""{"metadata":{"channelMetadataRenderer":{"vanityChannelUrl":"https://www.youtube.com/@%C3%84bc"}}}""")
        assertEquals("@Äbc", ChannelHandle.fromYoutube(response))
    }

    @Test
    fun readsCurrentAndLegacyHeaderHandles() {
        val current = JsonParser.`object`().from("""{"header":{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"@Travel"}},{"text":{"content":"1M subscribers"}}]}]}}}}}}}""")
        assertEquals("@Travel", ChannelHandle.fromYoutube(current))
        val legacy = JsonParser.`object`().from("""{"header":{"c4TabbedHeaderRenderer":{"channelHandleText":{"runs":[{"text":"@OldStyle"}]}}}}""")
        assertEquals("@OldStyle", ChannelHandle.fromYoutube(legacy))
    }

    @Test
    fun missingOrUnrelatedMetadataDoesNotInventAHandle() {
        assertNull(ChannelHandle.fromYoutube(JsonParser.`object`().from("""{"name":"@NotAHandle","description":"@someone"}""")))
        assertNull(ChannelHandle.fromYoutube(null))
        assertNull(ChannelHandle.fromUrl("https://www.youtube.com/channel/UC123"))
        assertNull(ChannelHandle.fromUrl("https://www.youtube.com/@bad%2Fpath"))
        assertNull(ChannelHandle.normalize("@two words"))
        assertNull(ChannelHandle.normalize("@"))
    }
}
