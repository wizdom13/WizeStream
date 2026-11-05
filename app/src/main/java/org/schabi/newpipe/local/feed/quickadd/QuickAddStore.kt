package org.schabi.newpipe.local.feed.quickadd

import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

/** Transactions serialize rapid taps and keep ownership checks beside the write. */
class QuickAddStore(private val database: AppDatabase, private val profileId: String) {
    data class Addition(val entry: PlaylistStreamEntity, val snapshot: List<PlaylistStreamEntity>)

    fun add(playlistId: Long, stream: StreamEntity): Addition? = database.runInTransaction<Addition?> {
        val playlist = database.playlistDAO().getPlaylistDirectForProfile(profileId, playlistId)
            ?: throw IllegalStateException("Playlist no longer available")
        val joins = database.playlistStreamDAO()
        val existing = database.streamDAO().getStreamDirect(stream.serviceId, stream.url)
        val entries = joins.getQuickAddEntries(playlistId)
        if (existing != null && entries.any { it.streamUid == existing.uid }) return@runInTransaction null
        val streamId = existing?.uid ?: database.streamDAO().upsert(stream.copy(uid = 0))
        val entry = PlaylistStreamEntity(playlistId, streamId, (entries.maxOfOrNull { it.index } ?: -1) + 1)
        joins.insert(entry)
        if (playlist.thumbnailStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
            playlist.thumbnailStreamId = streamId
            database.playlistDAO().updateForProfile(profileId, playlist)
        }
        Addition(entry, entries + entry)
    }

    /** Decline after a reorder or edit rather than removing somebody else's occurrence. */
    fun undo(addition: Addition): Boolean = database.runInTransaction<Boolean> {
        val playlist = database.playlistDAO().getPlaylistDirectForProfile(profileId, addition.entry.playlistUid)
            ?: return@runInTransaction false
        val joins = database.playlistStreamDAO()
        if (joins.getQuickAddEntries(playlist.uid) != addition.snapshot) return@runInTransaction false
        joins.delete(addition.entry)
        if (playlist.thumbnailStreamId == addition.entry.streamUid && !playlist.isThumbnailPermanent) {
            playlist.thumbnailStreamId = addition.snapshot.firstOrNull { it != addition.entry }?.streamUid
                ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
            database.playlistDAO().updateForProfile(profileId, playlist)
        }
        true
    }
}
