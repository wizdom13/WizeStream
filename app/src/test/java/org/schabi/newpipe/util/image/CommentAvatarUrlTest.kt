/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentAvatarUrlTest {
    @Test
    fun `absolute avatar URLs are retained and trimmed`() {
        assertEquals(
            "https://yt3.ggpht.com/avatar",
            normalizeCommentAvatarUrl("  https://yt3.ggpht.com/avatar  ")
        )
        assertEquals(
            "http://example.com/avatar.jpg",
            normalizeCommentAvatarUrl("http://example.com/avatar.jpg")
        )
    }

    @Test
    fun `scheme-relative avatar URLs use HTTPS`() {
        assertEquals(
            "https://yt3.ggpht.com/avatar",
            normalizeCommentAvatarUrl("//yt3.ggpht.com/avatar")
        )
    }

    @Test
    fun `missing and unsupported avatar URLs use the placeholder`() {
        assertNull(normalizeCommentAvatarUrl(null))
        assertNull(normalizeCommentAvatarUrl("  "))
        assertNull(normalizeCommentAvatarUrl("content://avatar"))
        assertNull(normalizeCommentAvatarUrl("javascript:alert(1)"))
    }
}
