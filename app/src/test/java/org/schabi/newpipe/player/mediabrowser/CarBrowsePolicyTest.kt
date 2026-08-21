package org.schabi.newpipe.player.mediabrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class CarBrowsePolicyTest {
    private val manyItems = (0 until 150).toList()

    @Test
    fun `limits each automotive surface to a distraction-safe result count`() {
        assertEquals(100, CarBrowsePolicy.browse(manyItems).size)
        assertEquals(20, CarBrowsePolicy.continueListening(manyItems).size)
        assertEquals(1, CarBrowsePolicy.resumption(manyItems).size)
        assertEquals(30, CarBrowsePolicy.search(manyItems).size)
    }

    @Test
    fun `preserves source order so the newest history item remains first`() {
        assertEquals(listOf(0, 1, 2), CarBrowsePolicy.continueListening(manyItems).take(3))
    }
}
