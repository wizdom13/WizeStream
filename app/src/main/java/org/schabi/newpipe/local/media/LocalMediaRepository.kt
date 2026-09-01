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
    fun query(access: LocalMediaAccess): LocalMediaLibrary = LocalMediaLibrary(
        audioItems = if (access.canReadAudio) queryAudio() else emptyList(),
        videoItems = if (access.canReadVideo) queryVideo() else emptyList()
    )

    private fun queryAudio(): List<LocalMediaItem> {
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
        ).apply {
            add(
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Audio.Media.RELATIVE_PATH
                } else {
                    MediaStore.Audio.Media.DATA
                }
            )
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
        }.toTypedArray()

        return queryCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            columns,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        ) { cursor ->
            val id = cursor.long(MediaStore.Audio.Media._ID)
            val albumId = cursor.long(MediaStore.Audio.Media.ALBUM_ID)
            val location = cursor.location()
            val track = splitMediaStoreTrack(cursor.int(MediaStore.Audio.Media.TRACK))
            LocalMediaItem(
                mediaStoreId = id,
                contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString(),
                title = cursor.title(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME
                ),
                artist = cursor.text(MediaStore.Audio.Media.ARTIST),
                album = cursor.text(MediaStore.Audio.Media.ALBUM),
                folder = location.folder,
                mimeType = cursor.text(MediaStore.Audio.Media.MIME_TYPE),
                durationSeconds = cursor.long(MediaStore.Audio.Media.DURATION) / 1_000L,
                addedAtSeconds = cursor.long(MediaStore.Audio.Media.DATE_ADDED),
                isVideo = false,
                thumbnailUri = albumId.takeIf { it > 0 }?.let {
                    ContentUris.withAppendedId(ALBUM_ART_URI, it).toString()
                },
                artistId = cursor.long(MediaStore.Audio.Media.ARTIST_ID),
                albumId = albumId,
                trackNumber = track.first,
                discNumber = track.second,
                relativePath = location.relativePath,
                volumeName = cursor.text(MediaStore.Audio.Media.VOLUME_NAME),
                sizeBytes = cursor.long(MediaStore.Audio.Media.SIZE)
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
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE
        ).apply {
            add(
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Video.Media.RELATIVE_PATH
                } else {
                    MediaStore.Video.Media.DATA
                }
            )
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Video.Media.VOLUME_NAME)
            }
        }.toTypedArray()

        return queryCollection(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            columns,
            null
        ) { cursor ->
            val id = cursor.long(MediaStore.Video.Media._ID)
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            ).toString()
            val location = cursor.location()
            LocalMediaItem(
                mediaStoreId = id,
                contentUri = uri,
                title = cursor.title(
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DISPLAY_NAME
                ),
                artist = "",
                album = "",
                folder = location.folder,
                mimeType = cursor.text(MediaStore.Video.Media.MIME_TYPE),
                durationSeconds = cursor.long(MediaStore.Video.Media.DURATION) / 1_000L,
                addedAtSeconds = cursor.long(MediaStore.Video.Media.DATE_ADDED),
                isVideo = true,
                thumbnailUri = uri,
                relativePath = location.relativePath,
                volumeName = cursor.text(MediaStore.Video.Media.VOLUME_NAME),
                sizeBytes = cursor.long(MediaStore.Video.Media.SIZE)
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

    private fun Cursor.text(column: String): String {
        return getColumnIndex(column).takeIf { it >= 0 }?.let(::getString).orEmpty()
            .takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty()
    }

    private fun Cursor.long(column: String): Long {
        return getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L
    }

    private fun Cursor.int(column: String): Int {
        return getColumnIndex(column).takeIf { it >= 0 }?.let(::getInt) ?: 0
    }

    private fun Cursor.title(titleColumn: String, displayNameColumn: String): String {
        return text(titleColumn).ifBlank { text(displayNameColumn).substringBeforeLast('.') }
    }

    private fun Cursor.location(): LocalMediaLocation {
        val column = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        return localMediaLocation(text(column), Build.VERSION.SDK_INT)
    }

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

internal data class LocalMediaLocation(val relativePath: String, val folder: String)

internal fun localMediaLocation(value: String, sdk: Int): LocalMediaLocation {
    val path = if (sdk >= 29) {
        value.trim('/').trim()
    } else {
        File(value).parent.orEmpty().trimEnd(File.separatorChar)
    }
    return LocalMediaLocation(path, path.substringAfterLast(File.separatorChar))
}

internal fun splitMediaStoreTrack(value: Int): Pair<Int, Int> {
    if (value <= 0) return 0 to 0
    return value % 1_000 to value / 1_000
}
