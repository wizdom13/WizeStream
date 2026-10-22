package org.schabi.newpipe.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ListSelectionOrderTest {
    @Test
    fun `moving scattered occurrences to a heading keeps their original order`() {
        val result = ListSelectionOrder.move(5, mapOf(0 to "First", 4 to "Second"), setOf(4, 0, 2), 5)
        assertEquals(listOf(1, 0, 2, 4, 3), result.itemIndices)
        assertEquals(mapOf(0 to "First", 2 to "Second"), result.labels)
    }

    @Test
    fun `moving all items preserves consecutive and trailing empty headings`() {
        val result = ListSelectionOrder.move(3, mapOf(0 to "A", 1 to "B", 5 to "C"), setOf(0, 1, 2), 6)
        assertEquals(listOf(0, 1, 2), result.itemIndices)
        assertEquals(mapOf(0 to "A", 1 to "B", 2 to "C"), result.labels)
    }

    @Test
    fun `moving to beginning leaves remaining occurrences and headings ordered`() {
        val result = ListSelectionOrder.move(4, mapOf(0 to "A", 3 to "B"), setOf(1, 3), 0)
        assertEquals(listOf(1, 3, 0, 2), result.itemIndices)
        assertEquals(mapOf(2 to "A", 4 to "B"), result.labels)
    }

    @Test
    fun `empty selection and insertion within selected block preserve layout`() {
        val labels = mapOf(0 to "A", 4 to "B")
        assertEquals(ListSelectionOrder.Result(listOf(0, 1, 2, 3), labels), ListSelectionOrder.move(4, labels, emptySet(), 6))
        assertEquals(ListSelectionOrder.Result(listOf(0, 1, 2, 3), labels), ListSelectionOrder.move(4, labels, setOf(0, 1), 2))
    }
}
