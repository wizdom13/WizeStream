/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.security.MessageDigest

/** Converts one structured preference category between application state and sync records. */
internal interface StructuredPreferenceCategoryAdapter {
    val category: StructuredPreferenceCategory

    fun snapshotHash(): String

    fun reconcile(bootstrap: Boolean)

    fun materialize()
}

internal fun structuredPreferenceDigest(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
