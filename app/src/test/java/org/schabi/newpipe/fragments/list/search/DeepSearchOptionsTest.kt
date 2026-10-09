package org.schabi.newpipe.fragments.list.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepSearchOptionsTest {
    @Test
    fun customPageCountsKeepTheExistingBound() {
        assertEquals(1, DeepSearchOptions.parsePages("1"))
        assertEquals(250, DeepSearchOptions.parsePages("250"))
        listOf("0", "251", "-1", "2.5", "", "999999999999").forEach {
            assertNull(DeepSearchOptions.parsePages(it))
        }
    }

    @Test
    fun delaysAcceptDecimalInputAndRejectInvalidOrUnboundedValues() {
        assertEquals(300L, DeepSearchOptions.parseDelaySeconds("0.3"))
        assertEquals(2500L, DeepSearchOptions.parseDelaySeconds("2,5"))
        assertEquals(10000L, DeepSearchOptions.parseDelaySeconds("10"))
        listOf("0", "0.299", "10.001", "NaN", "Infinity", "1e99", "").forEach {
            assertNull(DeepSearchOptions.parseDelaySeconds(it))
        }
    }
}
