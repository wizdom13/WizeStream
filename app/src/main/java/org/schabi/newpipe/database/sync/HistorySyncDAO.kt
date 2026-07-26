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
abstract class HistorySyncDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertChange(change: HistorySyncChangeEntity): Long

    @Query(
        """
        SELECT * FROM history_sync_changes
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
    ): List<HistorySyncChangeEntity>

    @Query(
        """
        SELECT COUNT(*) FROM history_sync_changes
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
            SELECT 1 FROM history_sync_changes
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
        SELECT DISTINCT origin_peer_id FROM history_sync_changes
        WHERE category = :category
        """
    )
    abstract fun getChangeOrigins(category: String): List<String>

    @Query(
        """
        SELECT COALESCE(MAX(lamport_version), 0) FROM history_sync_changes
        WHERE category = :category
        """
    )
    abstract fun getMaximumLamportVersion(category: String): Long

    @Query(
        """
        SELECT * FROM history_sync_records
        WHERE category = :category AND record_id = :recordId
        """
    )
    abstract fun getRecord(category: String, recordId: String): HistorySyncRecordEntity?

    @Query("SELECT * FROM history_sync_records WHERE category = :category")
    abstract fun getRecords(category: String): List<HistorySyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertRecord(record: HistorySyncRecordEntity)

    @Query(
        """
        SELECT * FROM history_sync_origin_state
        WHERE category = :category AND origin_peer_id = :originPeerId
        """
    )
    abstract fun getOriginState(
        category: String,
        originPeerId: String
    ): HistorySyncOriginStateEntity?

    @Query(
        """
        SELECT * FROM history_sync_origin_state
        WHERE category = :category
        """
    )
    abstract fun getOriginStates(category: String): List<HistorySyncOriginStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertOriginState(state: HistorySyncOriginStateEntity)

    @Query(
        """
        SELECT * FROM history_sync_peer_state
        WHERE category = :category AND peer_id = :peerId
        """
    )
    abstract fun getPeerStates(
        category: String,
        peerId: String
    ): List<HistorySyncPeerStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPeerState(state: HistorySyncPeerStateEntity)

    @Query(
        """
        DELETE FROM history_sync_peer_state
        WHERE category = :category
        """
    )
    abstract fun deletePeerStates(category: String)
}
