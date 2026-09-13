package org.schabi.newpipe.fragments.list.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class DiscoveryPersonalizationTest {
    private fun item(title: String, url: String = title): StreamInfoItem = StreamInfoItem(0, url, title, StreamType.VIDEO_STREAM)

    @Test
    fun interestsPrioritizeMatchesWithoutMutatingSourceOrDiscardingOtherTopics() {
        val original = listOf(item("Music"), item("Space science"), item("News"), item("Science today"))
        val result = DiscoveryRules(interests = listOf("science")).apply(original, emptySet())
        assertEquals(listOf("Space science", "Science today", "Music", "News"), result.map { it.name })
        assertEquals("Music", original.first().name)
        assertEquals(original, DiscoveryRules().apply(original, emptySet()))
    }

    @Test
    fun hiddenTermsMatchChannelsAndAllVisibilityControlsCompose() {
        val items = listOf(
            item("Blocked channel").apply { uploaderName = "Spam" },
            item("Short").apply { isShortFormContent = true },
            StreamInfoItem(0, "live", "Live", StreamType.LIVE_STREAM),
            item("Watched", "watched"),
            item("Keep")
        )
        val result = DiscoveryRules(hidden = listOf("spam"), hideWatched = true, hideShorts = true, hideLive = true)
            .apply(items, setOf("0:watched"))
        assertEquals(listOf("Keep"), result.map { it.name })
        assertEquals(5, items.size)
    }
}
