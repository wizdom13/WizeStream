/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediabrowser

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal object CarAudioSearchResultSelector {
    fun firstPlayable(items: List<InfoItem>): StreamInfoItem? = items
        .filterIsInstance<StreamInfoItem>()
        .firstOrNull()
}
