package org.schabi.newpipe.local.feed

import org.junit.Test

class FeedFragmentLifecycleTest {
    @Test
    fun `state rendering before view creation is ignored`() {
        val fragment = FeedFragment()

        fragment.showLoading()
        fragment.hideLoading()
        fragment.showEmptyState()
        fragment.handleError()
    }
}
