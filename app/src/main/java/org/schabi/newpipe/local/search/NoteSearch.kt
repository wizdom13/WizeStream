/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.search

import org.schabi.newpipe.database.learning.model.BookmarkNote
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

object NoteSearch {
    @JvmStatic
    fun bookmarkStreams(
        notes: List<BookmarkNote>,
        query: String,
        playlistKeys: Set<String>
    ): List<PlaylistStreamEntry> {
        if (!ContextualSearchHelper.isActive(query)) return emptyList()
        return notes.asSequence()
            .filter { it.playlistKey in playlistKeys && ContextualSearchHelper.matches(query, it.text) }
            .distinctBy { it.stream.uid }
            .map { PlaylistStreamEntry(it.stream, 0, it.stream.uid, 0) }
            .toList()
    }

    @JvmStatic
    fun textByStream(notes: List<LearningNoteEntity>): Map<Long, String> = notes.groupBy { it.streamId }.mapValues { (_, values) -> values.joinToString("\n") { it.noteText } }
}
