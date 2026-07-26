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
abstract class StructuredPreferenceSyncDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertChange(change: StructuredPreferenceSyncChangeEntity): Long

    @Query(
        """
        SELECT * FROM structured_preference_sync_changes
        WHERE category = :category
            AND origin_peer_id = :originPeerId
            AND origin_revision > :revision
        ORDER BY origin_revision ASC
        LIMIT :limit
        """
    )
    abstract fun getChangesAfter(
        category: String,
        originPeerId: String,
        revision: Long,
        limit: Int
    ): List<StructuredPreferenceSyncChangeEntity>

    @Query(
        """
        SELECT COUNT(*) FROM structured_preference_sync_changes
        WHERE category = :category
            AND origin_peer_id = :originPeerId
            AND origin_revision > :revision
        """
    )
    abstract fun countChangesAfter(
        category: String,
        originPeerId: String,
        revision: Long
    ): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM structured_preference_sync_changes
            WHERE category = :category
                AND origin_peer_id = :originPeerId
                AND origin_revision = :revision
        )
        """
    )
    abstract fun hasChange(
        category: String,
        originPeerId: String,
        revision: Long
    ): Boolean

    @Query(
        """
        SELECT DISTINCT origin_peer_id FROM structured_preference_sync_changes
        WHERE category = :category
        """
    )
    abstract fun getChangeOrigins(category: String): List<String>

    @Query(
        """
        SELECT COALESCE(MAX(lamport_version), 0)
        FROM structured_preference_sync_changes
        WHERE category = :category
        """
    )
    abstract fun getMaximumLamportVersion(category: String): Long

    @Query(
        """
        SELECT * FROM structured_preference_sync_records
        WHERE category = :category AND record_id = :recordId
        """
    )
    abstract fun getRecord(
        category: String,
        recordId: String
    ): StructuredPreferenceSyncRecordEntity?

    @Query(
        """
        SELECT * FROM structured_preference_sync_records
        WHERE category = :category
        """
    )
    abstract fun getRecords(
        category: String
    ): List<StructuredPreferenceSyncRecordEntity>

    @Query(
        """
        SELECT * FROM structured_preference_sync_records
        WHERE category = :category AND record_type = :recordType
        """
    )
    abstract fun getRecordsByType(
        category: String,
        recordType: String
    ): List<StructuredPreferenceSyncRecordEntity>

    @Query(
        """
        SELECT * FROM structured_preference_sync_records
        WHERE category = :category AND parent_record_id = :parentRecordId
        """
    )
    abstract fun getChildRecords(
        category: String,
        parentRecordId: String
    ): List<StructuredPreferenceSyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertRecord(record: StructuredPreferenceSyncRecordEntity)

    @Query(
        """
        SELECT * FROM structured_preference_sync_origin_state
        WHERE category = :category AND origin_peer_id = :originPeerId
        """
    )
    abstract fun getOriginState(
        category: String,
        originPeerId: String
    ): StructuredPreferenceSyncOriginStateEntity?

    @Query(
        """
        SELECT * FROM structured_preference_sync_origin_state
        WHERE category = :category
        """
    )
    abstract fun getOriginStates(
        category: String
    ): List<StructuredPreferenceSyncOriginStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertOriginState(state: StructuredPreferenceSyncOriginStateEntity)

    @Query(
        """
        SELECT * FROM structured_preference_sync_peer_state
        WHERE category = :category AND peer_id = :peerId
        """
    )
    abstract fun getPeerStates(
        category: String,
        peerId: String
    ): List<StructuredPreferenceSyncPeerStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPeerState(state: StructuredPreferenceSyncPeerStateEntity)

    @Query("DELETE FROM structured_preference_sync_peer_state")
    abstract fun deleteAllPeerStates()

    @Query("SELECT * FROM structured_preference_sync_feed_group_map")
    abstract fun getFeedGroupMappings(): List<StructuredPreferenceSyncFeedGroupMapEntity>

    @Query(
        """
        SELECT * FROM structured_preference_sync_feed_group_map
        WHERE group_record_id = :recordId
        """
    )
    abstract fun getFeedGroupMapping(
        recordId: String
    ): StructuredPreferenceSyncFeedGroupMapEntity?

    @Query(
        """
        SELECT * FROM structured_preference_sync_feed_group_map
        WHERE group_uid = :groupUid
        """
    )
    abstract fun getFeedGroupMapping(
        groupUid: Long
    ): StructuredPreferenceSyncFeedGroupMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertFeedGroupMapping(
        mapping: StructuredPreferenceSyncFeedGroupMapEntity
    )

    @Query(
        """
        SELECT * FROM structured_preference_sync_local_state
        WHERE category = :category
        """
    )
    abstract fun getLocalState(
        category: String
    ): StructuredPreferenceSyncLocalStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertLocalState(state: StructuredPreferenceSyncLocalStateEntity)
}
