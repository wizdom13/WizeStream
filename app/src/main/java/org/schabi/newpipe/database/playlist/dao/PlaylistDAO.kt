/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Dao
interface PlaylistDAO : BasicDAO<PlaylistEntity> {

    @Query("SELECT * FROM playlists")
    override fun getAll(): Flowable<List<PlaylistEntity>>

    @Query(
        """
        SELECT * FROM playlists
        WHERE profile_id = :profileId
        ORDER BY display_index
        """
    )
    fun getAllForProfile(profileId: String): Flowable<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    fun getAllDirect(): List<PlaylistEntity>

    @Query(
        """
        SELECT * FROM playlists
        WHERE profile_id = :profileId
        ORDER BY display_index
        """
    )
    fun getAllDirectForProfile(profileId: String): List<PlaylistEntity>

    @Query("DELETE FROM playlists")
    override fun deleteAll(): Int

    @Query("DELETE FROM playlists WHERE profile_id = :profileId")
    fun deleteAllForProfile(profileId: String): Int

    override fun listByService(serviceId: Int): Flowable<List<PlaylistEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<MutableList<PlaylistEntity>>

    @Query(
        """
        SELECT * FROM playlists
        WHERE profile_id = :profileId AND uid = :playlistId
        """
    )
    fun getPlaylistForProfile(
        profileId: String,
        playlistId: Long
    ): Flowable<MutableList<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE uid = :playlistId")
    fun getPlaylistDirect(playlistId: Long): PlaylistEntity?

    @Query(
        """
        SELECT * FROM playlists
        WHERE profile_id = :profileId AND uid = :playlistId
        """
    )
    fun getPlaylistDirectForProfile(
        profileId: String,
        playlistId: Long
    ): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE uid = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @Query(
        """
        DELETE FROM playlists
        WHERE profile_id = :profileId AND uid = :playlistId
        """
    )
    fun deletePlaylistForProfile(profileId: String, playlistId: Long): Int

    @get:Query("SELECT COUNT(*) FROM playlists")
    val count: Flowable<Long>

    @Query("SELECT COUNT(*) FROM playlists WHERE profile_id = :profileId")
    fun getCountForProfile(profileId: String): Flowable<Long>

    @Transaction
    fun updateForProfile(profileId: String, playlist: PlaylistEntity): Int {
        if (getPlaylistDirectForProfile(profileId, playlist.uid) == null) {
            return 0
        }
        playlist.profileId = profileId
        return update(playlist)
    }

    @Transaction
    fun upsertPlaylist(playlist: PlaylistEntity): Long {
        if (playlist.uid == -1L) {
            return insert(playlist)
        }
        update(playlist)
        return playlist.uid
    }
}
