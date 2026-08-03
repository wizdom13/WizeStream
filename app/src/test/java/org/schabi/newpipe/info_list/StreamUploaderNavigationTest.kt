/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class StreamUploaderNavigationTest {
    @Test
    fun validUploaderCreatesChannelTargetWithAvatar() {
        val stream = StreamInfoItem(3, "https://example.com/video", "Video", StreamType.VIDEO_STREAM)
        stream.uploaderName = "Creator"
        stream.uploaderUrl = "https://example.com/channel"
        stream.uploaderAvatarUrl = "https://example.com/avatar.jpg"

        val channel = StreamUploaderNavigation.fromStream(stream)!!

        assertEquals(3, channel.serviceId)
        assertEquals("https://example.com/channel", channel.url)
        assertEquals("Creator", channel.name)
        assertEquals("https://example.com/avatar.jpg", channel.thumbnailUrl)
    }

    @Test
    fun missingOrBlankUploaderUrlDoesNotCreateChannelTarget() {
        assertNull(StreamUploaderNavigation.create(0, null, "Creator", null))
        assertNull(StreamUploaderNavigation.create(0, "   ", "Creator", null))
    }

    @Test
    fun missingNameAndAvatarStillCreatesSafeChannelTarget() {
        val channel = StreamUploaderNavigation.create(
            0,
            "https://example.com/channel",
            null,
            null
        )!!

        assertEquals("", channel.name)
        assertNull(channel.thumbnailUrl)
    }

    @Test
    fun streamEntityPreservesUploaderAvatarAcrossDatabaseConversion() {
        val stream = StreamInfoItem(3, "https://example.com/video", "Video", StreamType.VIDEO_STREAM)
        stream.uploaderName = "Creator"
        stream.uploaderUrl = "https://example.com/channel"
        stream.uploaderAvatarUrl = "https://example.com/avatar.jpg"

        val entity = StreamEntity(stream)
        val restored = entity.toStreamInfoItem()

        assertEquals("https://example.com/avatar.jpg", entity.uploaderAvatarUrl)
        assertEquals("https://example.com/avatar.jpg", restored.uploaderAvatarUrl)
    }
}
