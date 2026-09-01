/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

data class LocalMediaLibrary(
    val audioItems: List<LocalMediaItem>,
    val videoItems: List<LocalMediaItem>
) {
    val allItems: List<LocalMediaItem>
        get() = audioItems + videoItems

    companion object {
        val EMPTY = LocalMediaLibrary(emptyList(), emptyList())
    }
}

data class LocalMediaLibraryState(
    val isLoading: Boolean,
    val library: LocalMediaLibrary = LocalMediaLibrary.EMPTY
)
