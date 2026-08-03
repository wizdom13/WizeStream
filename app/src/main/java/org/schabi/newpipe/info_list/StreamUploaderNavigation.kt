/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list

import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object StreamUploaderNavigation {
    @JvmStatic
    fun fromStream(item: StreamInfoItem): ChannelInfoItem? = create(
        item.serviceId,
        item.uploaderUrl,
        item.uploaderName,
        item.uploaderAvatarUrl
    )

    @JvmStatic
    fun create(
        serviceId: Int,
        uploaderUrl: String?,
        uploaderName: String?,
        uploaderAvatarUrl: String?
    ): ChannelInfoItem? {
        val url = uploaderUrl?.takeIf { it.isNotBlank() } ?: return null
        return ChannelInfoItem(serviceId, url, uploaderName.orEmpty()).apply {
            thumbnailUrl = uploaderAvatarUrl
        }
    }
}
