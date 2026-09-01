/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.Context
import android.net.Uri

class LocalMediaTreeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun roots(): Set<String> = preferences.getStringSet(ROOTS_KEY, emptySet())
        ?.toSet()
        .orEmpty()

    fun add(uri: Uri) {
        preferences.edit().putStringSet(ROOTS_KEY, roots() + uri.toString()).apply()
    }

    fun remove(uri: String) {
        preferences.edit().putStringSet(ROOTS_KEY, roots() - uri).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "local_media_document_roots"
        private const val ROOTS_KEY = "roots"
    }
}
