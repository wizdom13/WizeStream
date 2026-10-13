/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.schabi.newpipe.database.stream.model.StreamEntity

data class BookmarkNote(
    @Embedded val stream: StreamEntity,
    @ColumnInfo(name = "note_text") val text: String,
    @ColumnInfo(name = "playlist_key") val playlistKey: String
)
