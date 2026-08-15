/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

class LocalMediaRepository(private val context: Context) {
    fun query(): List<LocalMediaItem> = buildList {
        addAll(queryAudio())
        addAll(queryVideo())
    }

    private fun queryAudio(): List<LocalMediaItem> {
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
        ).apply {
            add(
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Audio.Media.RELATIVE_PATH
                } else {
                    MediaStore.Audio.Media.DATA
                }
            )
        }.toTypedArray()

        return queryCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            columns,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        ) { cursor ->
            val id = cursor.long(MediaStore.Audio.Media._ID)
            val albumId = cursor.long(MediaStore.Audio.Media.ALBUM_ID)
            LocalMediaItem(
                mediaStoreId = id,
                contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString(),
                title = cursor.title(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME),
                artist = cursor.text(MediaStore.Audio.Media.ARTIST),
                album = cursor.text(MediaStore.Audio.Media.ALBUM),
                folder = cursor.folder(),
                mimeType = cursor.text(MediaStore.Audio.Media.MIME_TYPE),
                durationSeconds = cursor.long(MediaStore.Audio.Media.DURATION) / 1_000L,
                addedAtSeconds = cursor.long(MediaStore.Audio.Media.DATE_ADDED),
                isVideo = false,
                thumbnailUri = albumId.takeIf { it > 0 }?.let {
                    ContentUris.withAppendedId(ALBUM_ART_URI, it).toString()
                }
            )
        }
    }

    private fun queryVideo(): List<LocalMediaItem> {
        val columns = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        ).apply {
            add(
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Video.Media.RELATIVE_PATH
                } else {
                    MediaStore.Video.Media.DATA
                }
            )
        }.toTypedArray()

        return queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns, null) { cursor ->
            val id = cursor.long(MediaStore.Video.Media._ID)
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            ).toString()
            LocalMediaItem(
                mediaStoreId = id,
                contentUri = uri,
                title = cursor.title(MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME),
                artist = "",
                album = "",
                folder = cursor.folder(),
                mimeType = cursor.text(MediaStore.Video.Media.MIME_TYPE),
                durationSeconds = cursor.long(MediaStore.Video.Media.DURATION) / 1_000L,
                addedAtSeconds = cursor.long(MediaStore.Video.Media.DATE_ADDED),
                isVideo = true,
                thumbnailUri = uri
            )
        }
    }

    private fun queryCollection(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        mapper: (Cursor) -> LocalMediaItem
    ): List<LocalMediaItem> {
        val result = mutableListOf<LocalMediaItem>()
        try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                while (cursor.moveToNext()) result += mapper(cursor)
            }
        } catch (_: SecurityException) {
            // Android 13+ may grant audio and video independently.
        }
        return result
    }

    private fun Cursor.text(column: String): String =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getString).orEmpty()
            .takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty()

    private fun Cursor.long(column: String): Long =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L

    private fun Cursor.title(titleColumn: String, displayNameColumn: String): String =
        text(titleColumn).ifBlank { text(displayNameColumn).substringBeforeLast('.') }

    private fun Cursor.folder(): String {
        val column = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val value = text(column)
        return if (Build.VERSION.SDK_INT >= 29) value.trimEnd('/').substringAfterLast('/')
        else File(value).parentFile?.name.orEmpty()
    }

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
