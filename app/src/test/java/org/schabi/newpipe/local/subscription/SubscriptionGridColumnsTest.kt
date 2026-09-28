package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionGridColumnsTest {
    @Test
    fun presetsIncludeAutoAndUsefulFixedCounts() {
        assertEquals(SubscriptionGridColumns.AUTO, SubscriptionGridColumns.presets.first())
        assertTrue(SubscriptionGridColumns.presets.containsAll(listOf(2, 3, 4, 5, 6)))
    }
}
