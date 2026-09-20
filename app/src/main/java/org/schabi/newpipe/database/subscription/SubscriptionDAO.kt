package org.schabi.newpipe.database.subscription

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.BasicDAO

@Dao
abstract class SubscriptionDAO : BasicDAO<SubscriptionEntity> {
    @Query("SELECT COUNT(*) FROM subscriptions")
    abstract fun rowCount(): Flowable<Long>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE profile_id = :profileId")
    abstract fun rowCountForProfile(profileId: String): Flowable<Long>

    @Query("SELECT * FROM subscriptions WHERE service_id = :serviceId")
    abstract override fun listByService(serviceId: Int): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId AND service_id = :serviceId
        """
    )
    abstract fun listByServiceForProfile(
        profileId: String,
        serviceId: Int
    ): Flowable<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE ASC")
    abstract override fun getAll(): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getAllForProfile(profileId: String): Flowable<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    abstract fun getAllDirect(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE profile_id = :profileId")
    abstract fun getAllDirectForProfile(profileId: String): List<SubscriptionEntity>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE name LIKE '%' || :filter || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsFiltered(
        filter: String
    ): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId
        AND name LIKE '%' || :filter || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsFilteredForProfile(
        profileId: String,
        filter: String
    ): Flowable<List<SubscriptionEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM subscriptions s
        LEFT JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id
        WHERE (fgs.subscription_id IS NULL OR fgs.group_id = :currentGroupId)
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsOnlyUngrouped(
        currentGroupId: Long
    ): Flowable<List<SubscriptionEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM subscriptions s
        LEFT JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id
        WHERE s.profile_id = :profileId
        AND (fgs.subscription_id IS NULL OR fgs.group_id = :currentGroupId)
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsOnlyUngroupedForProfile(
        profileId: String,
        currentGroupId: Long
    ): Flowable<List<SubscriptionEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM subscriptions s
        LEFT JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id
        WHERE (fgs.subscription_id IS NULL OR fgs.group_id = :currentGroupId)
        AND s.name LIKE '%' || :filter || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsOnlyUngroupedFiltered(
        currentGroupId: Long,
        filter: String
    ): Flowable<List<SubscriptionEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM subscriptions s
        LEFT JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id
        WHERE s.profile_id = :profileId
        AND (fgs.subscription_id IS NULL OR fgs.group_id = :currentGroupId)
        AND s.name LIKE '%' || :filter || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsOnlyUngroupedFilteredForProfile(
        profileId: String,
        currentGroupId: Long,
        filter: String
    ): Flowable<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    abstract fun getSubscriptionFlowable(
        serviceId: Int,
        url: String
    ): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId
        AND url LIKE :url
        AND service_id = :serviceId
        """
    )
    abstract fun getSubscriptionFlowableForProfile(
        profileId: String,
        serviceId: Int,
        url: String
    ): Flowable<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    abstract fun getSubscription(serviceId: Int, url: String): Maybe<SubscriptionEntity>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId
        AND url LIKE :url
        AND service_id = :serviceId
        """
    )
    abstract fun getSubscriptionForProfile(
        profileId: String,
        serviceId: Int,
        url: String
    ): Maybe<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE url = :url AND service_id = :serviceId")
    abstract fun getSubscriptionDirect(serviceId: Int, url: String): SubscriptionEntity?

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId
        AND url = :url
        AND service_id = :serviceId
        """
    )
    abstract fun getSubscriptionDirectForProfile(
        profileId: String,
        serviceId: Int,
        url: String
    ): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE uid = :subscriptionId")
    abstract fun getSubscription(subscriptionId: Long): SubscriptionEntity

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE profile_id = :profileId AND uid = :subscriptionId
        """
    )
    abstract fun getSubscriptionForProfile(
        profileId: String,
        subscriptionId: Long
    ): SubscriptionEntity?

    @Query("DELETE FROM subscriptions")
    abstract override fun deleteAll(): Int

    @Query("DELETE FROM subscriptions WHERE profile_id = :profileId")
    abstract fun deleteAllForProfile(profileId: String): Int

    @Query("DELETE FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    abstract fun deleteSubscription(serviceId: Int, url: String): Int

    @Query(
        """
        DELETE FROM subscriptions
        WHERE profile_id = :profileId
        AND url LIKE :url
        AND service_id = :serviceId
        """
    )
    abstract fun deleteSubscriptionForProfile(
        profileId: String,
        serviceId: Int,
        url: String
    ): Int

    @Query(
        """
        SELECT uid FROM subscriptions
        WHERE profile_id = :profileId
        AND url LIKE :url
        AND service_id = :serviceId
        """
    )
    internal abstract fun getSubscriptionIdInternal(
        profileId: String,
        serviceId: Int,
        url: String
    ): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertAllInternal(
        entities: List<SubscriptionEntity>
    ): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertIgnore(entity: SubscriptionEntity): Long

    @Transaction
    open fun upsertAll(entities: List<SubscriptionEntity>): List<SubscriptionEntity> {
        val insertUidList = silentInsertAllInternal(entities)

        insertUidList.forEachIndexed { index: Int, uidFromInsert: Long ->
            val entity = entities[index]

            if (uidFromInsert != -1L) {
                entity.uid = uidFromInsert
            } else {
                val subscriptionIdFromDb = getSubscriptionIdInternal(
                    entity.profileId,
                    entity.serviceId,
                    entity.url!!
                ) ?: error("Subscription cannot be null just after insertion.")
                entity.uid = subscriptionIdFromDb

                update(entity)
            }
        }

        return entities
    }
}
