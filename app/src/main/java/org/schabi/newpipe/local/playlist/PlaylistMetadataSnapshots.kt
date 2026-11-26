package org.schabi.newpipe.local.playlist

import android.database.AbstractWindowedCursor
import android.database.Cursor
import io.reactivex.rxjava3.core.Flowable
import java.util.concurrent.Callable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

/** Reads cached playlist rows before Room's observers can queue behind a feed write transaction. */
object PlaylistMetadataSnapshots {
    @JvmStatic
    fun local(database: AppDatabase, profileId: String): Flowable<MutableList<PlaylistMetadataEntry>> {
        val dao = database.playlistStreamDAO()
        return observe(
            database,
            { dao.getPlaylistMetadataCursorForProfile(profileId) },
            dao.getPlaylistMetadataForProfile(profileId),
            { cursor ->
                PlaylistMetadataEntry(
                    uid = cursor.long(PlaylistEntity.PLAYLIST_ID),
                    orderingName = cursor.string(PlaylistEntity.PLAYLIST_NAME),
                    thumbnailUrl = cursor.string(PlaylistEntity.PLAYLIST_THUMBNAIL_URL),
                    displayIndex = cursor.nullableLong(PlaylistEntity.PLAYLIST_DISPLAY_INDEX),
                    isThumbnailPermanent = cursor.nullableLong(PlaylistEntity.PLAYLIST_THUMBNAIL_PERMANENT)?.let { it != 0L },
                    thumbnailStreamId = cursor.nullableLong(PlaylistEntity.PLAYLIST_THUMBNAIL_STREAM_ID),
                    streamCount = cursor.long(PlaylistMetadataEntry.PLAYLIST_STREAM_COUNT)
                )
            },
            { LocalKey(it.uid, it.orderingName, it.thumbnailUrl, it.displayIndex, it.isThumbnailPermanent, it.thumbnailStreamId, it.streamCount) }
        )
    }

    fun remote(database: AppDatabase, profileId: String): Flowable<MutableList<PlaylistRemoteEntity>> {
        val dao = database.playlistRemoteDAO()
        return observe(
            database,
            { dao.getPlaylistsCursorForProfile(profileId) },
            dao.getPlaylistsForProfile(profileId),
            { cursor ->
                PlaylistRemoteEntity(
                    uid = cursor.long(PlaylistRemoteEntity.REMOTE_PLAYLIST_ID),
                    serviceId = cursor.long(PlaylistRemoteEntity.REMOTE_PLAYLIST_SERVICE_ID).toInt(),
                    orderingName = cursor.string(PlaylistRemoteEntity.REMOTE_PLAYLIST_NAME),
                    url = cursor.string(PlaylistRemoteEntity.REMOTE_PLAYLIST_URL),
                    thumbnailUrl = cursor.string(PlaylistRemoteEntity.REMOTE_PLAYLIST_THUMBNAIL_URL),
                    uploader = cursor.string(PlaylistRemoteEntity.REMOTE_PLAYLIST_UPLOADER_NAME),
                    displayIndex = cursor.long(PlaylistRemoteEntity.REMOTE_PLAYLIST_DISPLAY_INDEX),
                    streamCount = cursor.nullableLong(PlaylistRemoteEntity.REMOTE_PLAYLIST_STREAM_COUNT),
                    profileId = cursor.string(PlaylistRemoteEntity.REMOTE_PLAYLIST_PROFILE_ID)!!
                )
            },
            { it.copy() }
        )
    }

    private fun <T : Any, K : Any> observe(
        database: AppDatabase,
        query: () -> Cursor,
        updates: Flowable<MutableList<T>>,
        row: (Cursor) -> T,
        key: (T) -> K
    ): Flowable<MutableList<T>> = Flowable.fromCallable { readSnapshot(database, query, row) }
        // fromCallable respects cancellation while reading. Room's first query after registering
        // its observer also covers changes committed between this snapshot and registration.
        .concatWith(updates)
        // Feed writes invalidate the shared streams table even when no playlist metadata changes.
        // Copy comparison keys because the playlist editor can mutate emitted display indices.
        .map { Snapshot(it, it.map(key)) }
        .distinctUntilChanged { before, after -> before.key == after.key }
        .map { it.items }

    private fun <T> readSnapshot(database: AppDatabase, query: () -> Cursor, row: (Cursor) -> T): MutableList<T> {
        query().use { cursor ->
            val count = cursor.count
            if (count == 0) return mutableListOf()
            val window = (cursor as? AbstractWindowedCursor)?.window
            if (window != null && window.startPosition == 0 && window.numRows == count) {
                // A complete window was filled by one SQLite read snapshot. In WAL mode it can
                // return committed metadata while another connection is still writing the feed.
                return readRows(cursor, row)
            }
        }
        // Refilling a partial CursorWindow can rerun the SELECT. Retain a transaction for large
        // results (and unknown cursor implementations), so counts, covers and ordering cannot mix.
        return database.runInTransaction(Callable { query().use { readRows(it, row) } })
    }

    private fun <T> readRows(cursor: Cursor, row: (Cursor) -> T): MutableList<T> {
        val items = ArrayList<T>(cursor.count)
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) items += row(cursor)
        return items
    }

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.nullableLong(column: String): Long? = getColumnIndexOrThrow(column).let {
        if (isNull(it)) null else getLong(it)
    }

    private fun Cursor.string(column: String): String? = getString(getColumnIndexOrThrow(column))

    private data class Snapshot<T, K>(val items: MutableList<T>, val key: List<K>)

    private data class LocalKey(
        val uid: Long,
        val name: String?,
        val thumbnail: String?,
        val index: Long?,
        val permanentThumbnail: Boolean?,
        val thumbnailStreamId: Long?,
        val streamCount: Long
    )
}
