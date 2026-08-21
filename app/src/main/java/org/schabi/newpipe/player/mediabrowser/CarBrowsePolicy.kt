/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediabrowser

internal object CarBrowsePolicy {
    const val MAX_BROWSE_ITEMS = 100
    const val MAX_CONTINUE_ITEMS = 20
    const val MAX_RESUMPTION_ITEMS = 1
    const val MAX_SEARCH_ITEMS = 30
    const val LOAD_TIMEOUT_SECONDS = 20L

    fun <T> browse(items: List<T>): List<T> = items.take(MAX_BROWSE_ITEMS)

    fun <T> continueListening(items: List<T>): List<T> = items.take(MAX_CONTINUE_ITEMS)

    fun <T> resumption(items: List<T>): List<T> = items.take(MAX_RESUMPTION_ITEMS)

    fun <T> search(items: List<T>): List<T> = items.take(MAX_SEARCH_ITEMS)
}
