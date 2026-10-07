package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.local.subscription.SubscriptionLayout.Entry

class SubscriptionSortModelTest {
    @Test
    fun bulkMovePreservesLayoutOrderOfSelectedChannelsAndAllUnselectedEntries() {
        val model = SubscriptionSortModel()
        model.replace(listOf(Entry("b"), Entry("separator:one", "One"), Entry("a"), Entry("hidden"), Entry("separator:two", "Two"), Entry("c")), listOf("a", "b", "hidden", "c"))
        model.toggle("c")
        model.toggle("b")
        assertTrue(model.moveSelected("separator:one"))
        assertEquals(listOf("separator:one", "b", "c", "a", "hidden", "separator:two"), model.entries.map { it.key })
        assertTrue(model.selected.isEmpty())
    }

    @Test
    fun filteringSelectionAndKeyBasedDraggingKeepHiddenChannels() {
        val model = SubscriptionSortModel()
        model.replace(emptyList(), listOf("a", "hidden", "b", "c"))
        model.toggle("hidden")
        model.selectVisible(setOf("a", "b"))
        assertEquals(setOf("a", "hidden", "b"), model.selected)
        model.selectVisible(setOf("a", "b"))
        assertEquals(setOf("hidden"), model.selected)
        assertTrue(model.move("b", "a"))
        assertEquals(listOf("b", "a", "hidden", "c"), model.entries.map { it.key })
    }

    @Test
    fun liveUpdatesDropDeletedSelectionsAndAppendNewChannelsWithoutResettingOrder() {
        val model = SubscriptionSortModel()
        model.replace(listOf(Entry("b"), Entry("a")), listOf("a", "b"))
        model.toggle("b")
        model.replace(model.entries, listOf("a", "new"))
        assertEquals(listOf("a", "new"), model.entries.map { it.key })
        assertTrue(model.selected.isEmpty())
        model.toggle("missing")
        assertTrue(model.selected.isEmpty())
    }

    @Test
    fun creatingRenamingAndRemovingASeparatorDoesNotChangeMembership() {
        val model = SubscriptionSortModel()
        model.replace(emptyList(), listOf("a", "b"))
        model.toggle("a")
        assertFalse(model.moveSelected("missing"))
        assertEquals(setOf("a"), model.selected)
        model.putSeparator("separator:new", " New ")
        assertTrue(model.moveSelected("separator:new"))
        assertEquals(listOf(Entry("b"), Entry("separator:new", "New"), Entry("a")), model.entries)
        model.putSeparator("separator:new", "Renamed")
        assertEquals("Renamed", model.entries[1].separator)
        model.removeSeparator("separator:new")
        assertEquals(listOf(Entry("b"), Entry("a")), model.entries)
    }
}
