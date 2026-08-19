/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.info_list.ItemViewMode

class LocalMediaFragmentTest {
    @Test
    fun `local media follows the configured item view mode`() {
        assertEquals(R.layout.list_stream_item, localMediaItemLayout(ItemViewMode.AUTO))
        assertEquals(R.layout.list_stream_item, localMediaItemLayout(ItemViewMode.LIST))
        assertEquals(R.layout.list_stream_grid_item, localMediaItemLayout(ItemViewMode.GRID))
        assertEquals(R.layout.list_stream_card_item, localMediaItemLayout(ItemViewMode.CARD))
    }
}
