package org.schabi.newpipe.local.feed

import org.junit.Test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class FeedFragmentLifecycleTest {
    @Test
    fun `global members only hide overrides feed visibility`() {
        assertTrue(FeedFragment.shouldHideMembersOnlyInFeed(true, true))
        assertTrue(FeedFragment.shouldHideMembersOnlyInFeed(true, false))
    }

    @Test
    fun `feed visibility still applies when global hide is off`() {
        assertFalse(FeedFragment.shouldHideMembersOnlyInFeed(false, true))
        assertTrue(FeedFragment.shouldHideMembersOnlyInFeed(false, false))
    }

    @Test
    fun `state rendering before view creation is ignored`() {
        val fragment = FeedFragment()

        fragment.showLoading()
        fragment.hideLoading()
        fragment.showEmptyState()
        fragment.handleError()
    }
}
