package org.schabi.newpipe.local.feed

import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon

class FeedDatabaseManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val feedTable = database.feedDAO()
    private val feedGroupTable = database.feedGroupDAO()
    private val streamTable = database.streamDAO()

    companion object {
        /**
         * Only items that are newer than this will be saved.
         */
        val FEED_OLDEST_ALLOWED_DATE: OffsetDateTime = LocalDate.now().minusWeeks(13)
            .atStartOfDay().atOffset(ZoneOffset.UTC)
    }

    fun groups() = feedGroupTable.getAll()

    fun database() = database

    fun getStreams(
        groupId: Long,
        includePlayedStreams: Boolean,
        includePartiallyPlayedStreams: Boolean,
        includeFutureStreams: Boolean,
        scope: FeedScope
    ): Maybe<List<StreamWithState>> {
        return feedTable.getStreams(
            groupId,
            includePlayedStreams,
            includePartiallyPlayedStreams,
            if (includeFutureStreams) null else OffsetDateTime.now(),
            scope.serviceId,
            scope.youtubeModeMask
        )
    }

    fun outdatedSubscriptions(outdatedThreshold: OffsetDateTime) = feedTable.getAllOutdated(outdatedThreshold)

    fun outdatedSubscriptionsForScope(
        scope: FeedScope,
        outdatedThreshold: OffsetDateTime
    ) = feedTable.getAllOutdatedForScope(
        scope.serviceId,
        scope.youtubeModeMask,
        outdatedThreshold
    )

    fun outdatedSubscriptionsWithNotificationMode(
        outdatedThreshold: OffsetDateTime,
        @NotificationMode notificationMode: Int
    ) = feedTable.getOutdatedWithNotificationMode(outdatedThreshold, notificationMode)

    fun notLoadedCount(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        scope: FeedScope
    ): Flowable<Long> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID ->
                feedTable.notLoadedCount(scope.serviceId, scope.youtubeModeMask)

            else ->
                feedTable.notLoadedCountForGroup(
                    groupId,
                    scope.serviceId,
                    scope.youtubeModeMask
                )
        }
    }

    fun outdatedSubscriptionsForGroup(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        outdatedThreshold: OffsetDateTime
    ) = feedTable.getAllOutdatedForGroup(groupId, outdatedThreshold)

    fun outdatedSubscriptionsForGroupAndScope(
        groupId: Long,
        scope: FeedScope,
        outdatedThreshold: OffsetDateTime
    ) = feedTable.getAllOutdatedForGroupAndScope(
        groupId,
        scope.serviceId,
        scope.youtubeModeMask,
        outdatedThreshold
    )

    fun markAsOutdated(subscriptionId: Long, youtubeModeMask: Int) {
        youtubeModeMasks(youtubeModeMask).forEach { modeMask ->
            feedTable.setLastUpdatedForSubscription(
                FeedLastUpdatedEntity(subscriptionId, modeMask, null)
            )
        }
    }

    fun doesStreamExist(stream: StreamInfoItem): Boolean {
        return streamTable.exists(stream.serviceId, stream.url)
    }

    fun upsertAll(
        subscriptionId: Long,
        items: List<StreamInfoItem>,
        youtubeModeMask: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        oldestAllowedDate: OffsetDateTime = FEED_OLDEST_ALLOWED_DATE
    ) {
        val itemsToInsert = items.mapNotNull { stream ->
            val uploadDate = stream.uploadDate

            when {
                uploadDate == null && stream.streamType == StreamType.LIVE_STREAM -> stream
                uploadDate != null && uploadDate.offsetDateTime() >= oldestAllowedDate -> stream
                else -> null
            }
        }

        val modeMasks = youtubeModeMasks(youtubeModeMask)
        modeMasks.forEach { feedTable.unlinkOldLivestreams(subscriptionId, it) }

        if (itemsToInsert.isNotEmpty()) {
            val streamEntities = itemsToInsert.map { StreamEntity(it) }
            val streamIds = streamTable.upsertAll(streamEntities)
            val feedEntities = streamIds.flatMap { streamId ->
                modeMasks.map { modeMask ->
                    FeedEntity(streamId, subscriptionId, modeMask)
                }
            }

            feedTable.insertAll(feedEntities)
        }

        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        modeMasks.forEach { modeMask ->
            feedTable.setLastUpdatedForSubscription(
                FeedLastUpdatedEntity(subscriptionId, modeMask, updatedAt)
            )
        }
    }

    fun removeOrphansOrOlderStreams(oldestAllowedDate: OffsetDateTime = FEED_OLDEST_ALLOWED_DATE) {
        feedTable.unlinkStreamsOlderThan(oldestAllowedDate)
        streamTable.deleteOrphans()
    }

    fun clear() {
        feedTable.deleteAll()
        val deletedOrphans = streamTable.deleteOrphans()
        if (DEBUG) {
            Log.d(
                this::class.java.simpleName,
                "clear() → streamTable.deleteOrphans() → $deletedOrphans"
            )
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Feed Groups
    // /////////////////////////////////////////////////////////////////////////

    fun subscriptionIdsForGroup(groupId: Long): Flowable<List<Long>> {
        return feedGroupTable.getSubscriptionIdsFor(groupId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>): Completable {
        return Completable
            .fromCallable { feedGroupTable.updateSubscriptionsForGroup(groupId, subscriptionIds) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun createGroup(name: String, icon: FeedGroupIcon): Maybe<Long> {
        return Maybe.fromCallable { feedGroupTable.insert(FeedGroupEntity(0, name, icon)) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun getGroup(groupId: Long): Maybe<FeedGroupEntity> {
        return feedGroupTable.getGroup(groupId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateGroup(feedGroupEntity: FeedGroupEntity): Completable {
        return Completable.fromCallable { feedGroupTable.update(feedGroupEntity) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun deleteGroup(groupId: Long): Completable {
        return Completable.fromCallable { feedGroupTable.delete(groupId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateGroupsOrder(groupIdList: List<Long>): Completable {
        var index = 0L
        val orderMap = groupIdList.associateBy({ it }, { index++ })

        return Completable.fromCallable { feedGroupTable.updateOrder(orderMap) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun oldestSubscriptionUpdate(
        groupId: Long,
        scope: FeedScope
    ): Flowable<List<OffsetDateTime?>> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID ->
                feedTable.oldestSubscriptionUpdateFromAll(
                    scope.serviceId,
                    scope.youtubeModeMask
                )

            else ->
                feedTable.oldestSubscriptionUpdate(
                    groupId,
                    scope.serviceId,
                    scope.youtubeModeMask
                )
        }
    }

    private fun youtubeModeMasks(mask: Int): List<Int> {
        return listOf(
            SubscriptionEntity.YOUTUBE_MODE_REGULAR,
            SubscriptionEntity.YOUTUBE_MODE_MUSIC
        ).filter { modeMask -> mask and modeMask != 0 }
            .ifEmpty { listOf(SubscriptionEntity.YOUTUBE_MODE_REGULAR) }
    }
}
