/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class PlaylistSyncDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertChange(change: PlaylistSyncChangeEntity): Long

    @Query(
        """
        SELECT * FROM playlist_sync_changes
        WHERE origin_peer_id = :originPeerId AND origin_revision > :revision
        ORDER BY origin_revision ASC
        LIMIT :limit
        """
    )
    abstract fun getChangesAfter(
        originPeerId: String,
        revision: Long,
        limit: Int
    ): List<PlaylistSyncChangeEntity>

    @Query(
        """
        SELECT COUNT(*) FROM playlist_sync_changes
        WHERE origin_peer_id = :originPeerId AND origin_revision > :revision
        """
    )
    abstract fun countChangesAfter(originPeerId: String, revision: Long): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_sync_changes
            WHERE origin_peer_id = :originPeerId AND origin_revision = :revision
        )
        """
    )
    abstract fun hasChange(originPeerId: String, revision: Long): Boolean

    @Query("SELECT DISTINCT origin_peer_id FROM playlist_sync_changes")
    abstract fun getChangeOrigins(): List<String>

    @Query("SELECT COALESCE(MAX(lamport_version), 0) FROM playlist_sync_changes")
    abstract fun getMaximumLamportVersion(): Long

    @Query("SELECT * FROM playlist_sync_records WHERE record_id = :recordId")
    abstract fun getRecord(recordId: String): PlaylistSyncRecordEntity?

    @Query("SELECT * FROM playlist_sync_records")
    abstract fun getAllRecords(): List<PlaylistSyncRecordEntity>

    @Query("SELECT * FROM playlist_sync_records WHERE parent_record_id = :parentRecordId")
    abstract fun getChildRecords(parentRecordId: String): List<PlaylistSyncRecordEntity>

    @Query("SELECT * FROM playlist_sync_records WHERE record_type = :recordType")
    abstract fun getRecordsByType(recordType: String): List<PlaylistSyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertRecord(record: PlaylistSyncRecordEntity)

    @Query(
        """
        SELECT * FROM playlist_sync_origin_state
        WHERE origin_peer_id = :originPeerId
        """
    )
    abstract fun getOriginState(originPeerId: String): PlaylistSyncOriginStateEntity?

    @Query("SELECT * FROM playlist_sync_origin_state")
    abstract fun getAllOriginStates(): List<PlaylistSyncOriginStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertOriginState(state: PlaylistSyncOriginStateEntity)

    @Query("SELECT * FROM playlist_sync_peer_state WHERE peer_id = :peerId")
    abstract fun getPeerStates(peerId: String): List<PlaylistSyncPeerStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPeerState(state: PlaylistSyncPeerStateEntity)

    @Query("DELETE FROM playlist_sync_peer_state")
    abstract fun deleteAllPeerStates()

    @Query("SELECT * FROM playlist_sync_local_map")
    abstract fun getAllLocalMappings(): List<PlaylistSyncLocalMapEntity>

    @Query(
        """
        SELECT * FROM playlist_sync_local_map
        WHERE playlist_record_id = :playlistRecordId
        """
    )
    abstract fun getLocalMapping(playlistRecordId: String): PlaylistSyncLocalMapEntity?

    @Query("SELECT * FROM playlist_sync_local_map WHERE playlist_uid = :playlistUid")
    abstract fun getLocalMapping(playlistUid: Long): PlaylistSyncLocalMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertLocalMapping(mapping: PlaylistSyncLocalMapEntity)
}
