/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

@Dao
interface PlaylistRemoteDAO : BasicDAO<PlaylistRemoteEntity> {

    @Query("SELECT * FROM remote_playlists")
    override fun getAll(): Flowable<List<PlaylistRemoteEntity>>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE profile_id = :profileId
        ORDER BY display_index
        """
    )
    fun getAllForProfile(profileId: String): Flowable<List<PlaylistRemoteEntity>>

    @Query("SELECT * FROM remote_playlists")
    fun getAllDirect(): List<PlaylistRemoteEntity>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE profile_id = :profileId
        ORDER BY display_index
        """
    )
    fun getAllDirectForProfile(profileId: String): List<PlaylistRemoteEntity>

    @Query("DELETE FROM remote_playlists")
    override fun deleteAll(): Int

    @Query("DELETE FROM remote_playlists WHERE profile_id = :profileId")
    fun deleteAllForProfile(profileId: String): Int

    @Query("SELECT * FROM remote_playlists WHERE service_id = :serviceId")
    override fun listByService(serviceId: Int): Flowable<List<PlaylistRemoteEntity>>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE profile_id = :profileId AND service_id = :serviceId
        """
    )
    fun listByServiceForProfile(
        profileId: String,
        serviceId: Int
    ): Flowable<List<PlaylistRemoteEntity>>

    @Query("SELECT * FROM remote_playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<PlaylistRemoteEntity>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE profile_id = :profileId AND uid = :playlistId
        """
    )
    fun getPlaylistForProfile(
        profileId: String,
        playlistId: Long
    ): Flowable<PlaylistRemoteEntity>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE url = :url AND service_id = :serviceId
        """
    )
    fun getPlaylist(
        serviceId: Long,
        url: String?
    ): Flowable<MutableList<PlaylistRemoteEntity>>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE profile_id = :profileId
        AND url = :url
        AND service_id = :serviceId
        """
    )
    fun getPlaylistForProfile(
        profileId: String,
        serviceId: Long,
        url: String?
    ): Flowable<MutableList<PlaylistRemoteEntity>>

    @get:Query("SELECT * FROM remote_playlists ORDER BY display_index")
    val playlists: Flowable<MutableList<PlaylistRemoteEntity>>

    @Query(
        """
        SELECT * FROM remote_playlists
        WHERE profile_id = :profileId
        ORDER BY display_index
        """
    )
    fun getPlaylistsForProfile(profileId: String): Flowable<MutableList<PlaylistRemoteEntity>>

    @Query(
        """
        SELECT uid FROM remote_playlists
        WHERE url = :url AND service_id = :serviceId
        """
    )
    fun getPlaylistIdInternal(serviceId: Long, url: String?): Long?

    @Query(
        """
        SELECT uid FROM remote_playlists
        WHERE profile_id = :profileId
        AND url = :url
        AND service_id = :serviceId
        """
    )
    fun getPlaylistIdForProfile(
        profileId: String,
        serviceId: Long,
        url: String?
    ): Long?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM remote_playlists
            WHERE profile_id = :profileId AND uid = :playlistId
        )
        """
    )
    fun playlistBelongsToProfile(profileId: String, playlistId: Long): Boolean

    @Transaction
    fun upsert(playlist: PlaylistRemoteEntity): Long {
        return upsertForProfile(playlist.profileId, playlist)
    }

    @Transaction
    fun upsertForProfile(
        profileId: String,
        playlist: PlaylistRemoteEntity
    ): Long {
        val playlistId = getPlaylistIdForProfile(
            profileId,
            playlist.serviceId.toLong(),
            playlist.url
        )
        playlist.profileId = profileId
        if (playlistId == null) {
            playlist.uid = 0
            return insert(playlist)
        }
        playlist.uid = playlistId
        update(playlist)
        return playlistId
    }

    @Transaction
    fun updateForProfile(
        profileId: String,
        playlist: PlaylistRemoteEntity
    ): Int {
        if (!playlistBelongsToProfile(profileId, playlist.uid)) {
            return 0
        }
        playlist.profileId = profileId
        return update(playlist)
    }

    @Query("DELETE FROM remote_playlists WHERE uid = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @Query(
        """
        DELETE FROM remote_playlists
        WHERE profile_id = :profileId AND uid = :playlistId
        """
    )
    fun deletePlaylistForProfile(profileId: String, playlistId: Long): Int
}
