package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionGridSpanPolicyTest {
    @Test
    fun fixedPresetOverridesAdaptiveGroupColumns() {
        assertEquals(2, SubscriptionGridSpanPolicy.resolve(2, 5))
        assertEquals(6, SubscriptionGridSpanPolicy.resolve(6, 3))
    }

    @Test
    fun autoKeepsAdaptiveGroupColumns() {
        assertEquals(
            4,
            SubscriptionGridSpanPolicy.resolve(SubscriptionGridColumns.AUTO, 4)
        )
    }
}
