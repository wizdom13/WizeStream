package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.schabi.newpipe.local.subscription.SubscriptionLayout.Entry

@RunWith(RobolectricTestRunner::class)
class SubscriptionLayoutTest {
    @Test
    fun retainsOrderAndKeepsNewChannelsBeforeFirstSeparator() {
        val saved = listOf(Entry("b"), Entry("separator:cars", "Cars"), Entry("deleted"), Entry("a"))
        assertEquals(
            listOf(Entry("b"), Entry("new"), Entry("separator:cars", "Cars"), Entry("a")),
            SubscriptionLayout.reconcile(saved, listOf("a", "b", "new"))
        )
    }

    @Test
    fun filteringCannotChangePersistedPositions() {
        val saved = listOf(Entry("b"), Entry("separator:cars", "Cars"), Entry("a"))
        val context = RuntimeEnvironment.getApplication()
        val store = SubscriptionLayout(context, "filter-test")
        store.save(saved)
        assertEquals(listOf(Entry("separator:cars", "Cars"), Entry("a")), SubscriptionLayout.reconcile(store.read(), listOf("a")))
        assertEquals(saved, store.read())
        assertEquals(saved, SubscriptionLayout.reconcile(store.read(), listOf("a", "b")))
    }

    @Test
    fun profileLayoutsAreIsolatedAndChangesAreObserved() {
        val context = RuntimeEnvironment.getApplication()
        val first = SubscriptionLayout(context, "first-profile")
        val second = SubscriptionLayout(context, "second-profile")
        first.save(emptyList())
        second.save(emptyList())
        val observer = first.observe().test()
        first.save(listOf(Entry("channel:0:url")))
        assertEquals(emptyList<Entry>(), second.read())
        observer.assertValues(emptyList(), listOf(Entry("channel:0:url")))
        observer.cancel()
    }

    @Test
    fun separatorNamesRoundTripAndInvalidSettingsFallBackToAlphabetical() {
        val entries = listOf(Entry("a"), Entry("separator:1", "Cars & \"movies\"\n日本語"))
        assertEquals(entries, SubscriptionLayout.decode(SubscriptionLayout.encode(entries)))
        assertEquals(emptyList<Entry>(), SubscriptionLayout.decode("broken"))
        assertEquals(listOf(Entry("a"), Entry("b")), SubscriptionLayout.reconcile(emptyList(), listOf("a", "b")))
    }

    @Test
    fun duplicatePositionsAreRemoved() {
        assertEquals(listOf(Entry("b"), Entry("a")), SubscriptionLayout.reconcile(listOf(Entry("b"), Entry("b")), listOf("a", "b")))
    }

    @Test
    fun mutedChannelsMoveStablyWithinEachSectionWithoutChangingSeparators() {
        val first = Entry("separator:first", "First")
        val empty = Entry("separator:empty", "Empty")
        val last = Entry("separator:last", "Last")
        val entries = listOf(Entry("muted-a"), Entry("active-b"), Entry("muted-c"), Entry("active-d"), first, empty, Entry("muted-e"), Entry("active-f"), last)
        val expected = listOf(Entry("active-b"), Entry("active-d"), Entry("muted-a"), Entry("muted-c"), first, empty, Entry("active-f"), Entry("muted-e"), last)
        val muted = setOf("muted-a", "muted-c", "muted-e")

        assertEquals(expected, SubscriptionLayout.moveMutedChannelsToBottom(entries, muted))
        assertEquals("muted-a", entries.first().key)
        assertEquals(expected, SubscriptionLayout.moveMutedChannelsToBottom(expected, muted))
    }

    @Test
    fun mutedOrderingUsesFullChannelKeysAndKeepsNoOpLayoutsUnchanged() {
        val firstService = Entry("channel:0:https://example.com/shared")
        val secondService = Entry("channel:1:https://example.com/shared")
        val separator = Entry("separator:section", "Section")
        val entries = listOf(separator, firstService, secondService)

        assertEquals(listOf(separator, secondService, firstService), SubscriptionLayout.moveMutedChannelsToBottom(entries, setOf(firstService.key, separator.key)))
        assertEquals(entries, SubscriptionLayout.moveMutedChannelsToBottom(entries, emptySet()))
        assertEquals(entries, SubscriptionLayout.moveMutedChannelsToBottom(entries, setOf(firstService.key, secondService.key)))
        assertEquals(emptyList<Entry>(), SubscriptionLayout.moveMutedChannelsToBottom(emptyList(), setOf(firstService.key)))
    }
}
