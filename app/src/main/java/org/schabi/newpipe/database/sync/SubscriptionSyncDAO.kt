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
abstract class SubscriptionSyncDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertChange(change: SubscriptionSyncChangeEntity): Long

    @Query(
        """
        SELECT * FROM subscription_sync_changes
        WHERE origin_peer_id = :originPeerId AND origin_revision > :revision
        ORDER BY origin_revision ASC
        LIMIT :limit
        """
    )
    abstract fun getChangesAfter(
        originPeerId: String,
        revision: Long,
        limit: Int
    ): List<SubscriptionSyncChangeEntity>

    @Query(
        """
        SELECT COUNT(*) FROM subscription_sync_changes
        WHERE origin_peer_id = :originPeerId AND origin_revision > :revision
        """
    )
    abstract fun countChangesAfter(originPeerId: String, revision: Long): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM subscription_sync_changes
            WHERE origin_peer_id = :originPeerId AND origin_revision = :revision
        )
        """
    )
    abstract fun hasChange(originPeerId: String, revision: Long): Boolean

    @Query("SELECT DISTINCT origin_peer_id FROM subscription_sync_changes")
    abstract fun getChangeOrigins(): List<String>

    @Query("SELECT COALESCE(MAX(lamport_version), 0) FROM subscription_sync_changes")
    abstract fun getMaximumLamportVersion(): Long

    @Query("SELECT * FROM subscription_sync_records WHERE record_id = :recordId")
    abstract fun getRecord(recordId: String): SubscriptionSyncRecordEntity?

    @Query("SELECT * FROM subscription_sync_records")
    abstract fun getAllRecords(): List<SubscriptionSyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertRecord(record: SubscriptionSyncRecordEntity)

    @Query(
        """
        SELECT * FROM subscription_sync_origin_state
        WHERE origin_peer_id = :originPeerId
        """
    )
    abstract fun getOriginState(originPeerId: String): SubscriptionSyncOriginStateEntity?

    @Query("SELECT * FROM subscription_sync_origin_state")
    abstract fun getAllOriginStates(): List<SubscriptionSyncOriginStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertOriginState(state: SubscriptionSyncOriginStateEntity)

    @Query("SELECT * FROM subscription_sync_peer_state WHERE peer_id = :peerId")
    abstract fun getPeerStates(peerId: String): List<SubscriptionSyncPeerStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPeerState(state: SubscriptionSyncPeerStateEntity)

    @Query("DELETE FROM subscription_sync_peer_state")
    abstract fun deleteAllPeerStates()
}
