package org.schabi.newpipe.local.subscription

import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy

class SubscriptionManager @JvmOverloads constructor(
    context: Context,
    private val profileId: String = ProfileManager.getActiveProfileId(context)
) {
    private val database = NewPipeDatabase.getInstance(context)
    private val subscriptionTable = database.subscriptionDAO()
    private val feedDatabaseManager = FeedDatabaseManager(context, profileId)
    private val currentScope = FeedScope.from(context)
    private val currentYoutubeModeMask = currentScope.youtubeModeMask
    private val subscriptionSyncStore by lazy {
        RoomSubscriptionSyncStore.get(context)
    }

    fun subscriptionTable(): SubscriptionDAO = subscriptionTable
    fun subscriptions() = subscriptionTable.getAllForProfile(profileId)

    fun getSubscriptionById(subscriptionId: Long): SubscriptionEntity? {
        return subscriptionTable.getSubscriptionForProfile(profileId, subscriptionId)
    }

    fun getSubscriptions(
        currentGroupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        filterQuery: String = "",
        showOnlyUngrouped: Boolean = false
    ): Flowable<List<SubscriptionEntity>> = getSubscriptionsForScope(
        currentScope,
        currentGroupId,
        filterQuery,
        showOnlyUngrouped
    )

    fun getSubscriptionsForScope(
        scope: FeedScope,
        currentGroupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        filterQuery: String = "",
        showOnlyUngrouped: Boolean = false
    ): Flowable<List<SubscriptionEntity>> {
        return when {
            filterQuery.isNotEmpty() -> {
                if (showOnlyUngrouped) {
                    subscriptionTable.getSubscriptionsOnlyUngroupedFilteredForProfile(
                        profileId,
                        currentGroupId,
                        filterQuery
                    )
                } else {
                    subscriptionTable.getSubscriptionsFilteredForProfile(profileId, filterQuery)
                }
            }

            showOnlyUngrouped ->
                subscriptionTable.getSubscriptionsOnlyUngroupedForProfile(
                    profileId,
                    currentGroupId
                )

            else -> subscriptionTable.getAllForProfile(profileId)
        }.map { subscriptions -> subscriptions.filter(scope::includes) }
    }

    fun upsertAll(infoList: List<Pair<ChannelInfo, ChannelTabInfo?>>) {
        val listEntities = infoList.map {
            val entity = SubscriptionEntity.from(it.first).apply {
                profileId = this@SubscriptionManager.profileId
            }
            if (entity.serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID) {
                entity.youtubeModeMask = currentYoutubeModeMask
            }
            subscriptionTable.getSubscriptionDirectForProfile(
                profileId,
                entity.serviceId,
                requireNotNull(entity.url)
            )
                ?.let { existing ->
                    entity.notificationMode = existing.notificationMode
                    entity.notificationKeywords = existing.notificationKeywords
                    entity.youtubeModeMask = existing.youtubeModeMask or
                        currentYoutubeModeMask
                }
            entity
        }
        subscriptionTable.upsertAll(listEntities)
        listEntities.forEach(::recordSubscriptionUpsert)

        database.runInTransaction {
            infoList.forEachIndexed { index, info ->
                val streams = info.second?.relatedItems?.filterIsInstance<StreamInfoItem>()
                    ?: emptyList()
                feedDatabaseManager.upsertAll(
                    listEntities[index].uid,
                    streams,
                    if (listEntities[index].serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID) {
                        currentYoutubeModeMask
                    } else {
                        SubscriptionEntity.YOUTUBE_MODE_REGULAR
                    },
                    uploaderAvatarUrl = listEntities[index].avatarUrl
                )
            }
        }
    }

    /**
     * Inserts imported subscriptions without replacing any existing local subscription data.
     *
     * @return the number of newly inserted subscriptions
     */
    fun insertImportedSubscriptions(entities: List<SubscriptionEntity>): Int {
        val insertedEntities = database.runInTransaction<List<SubscriptionEntity>> {
            entities.mapNotNull { entity ->
                entity.profileId = profileId
                if (entity.serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID) {
                    entity.youtubeModeMask = currentYoutubeModeMask
                }
                val uid = subscriptionTable.insertIgnore(entity)
                if (uid == -1L) {
                    null
                } else {
                    entity.uid = uid
                    entity
                }
            }
        }
        insertedEntities.forEach(::recordSubscriptionUpsert)
        return insertedEntities.size
    }

    fun updateChannelInfo(info: ChannelInfo): Completable {
        return subscriptionTable
            .getSubscriptionForProfile(profileId, info.serviceId, info.url)
            .flatMapCompletable {
                Completable.fromRunnable {
                    it.apply {
                        name = info.name
                        avatarUrl = ImageStrategy.imageListToDbUrl(info.avatars)
                        description = info.description
                        subscriberCount = info.subscriberCount
                    }
                    subscriptionTable.update(it)
                }
            }
    }

    fun updateNotificationMode(serviceId: Int, url: String, @NotificationMode mode: Int): Completable {
        return subscriptionTable().getSubscriptionForProfile(profileId, serviceId, url)
            .flatMapCompletable { entity ->
                updateNotificationSettings(
                    serviceId,
                    url,
                    mode,
                    entity.notificationKeywords
                )
            }
    }

    fun updateNotificationSettings(
        serviceId: Int,
        url: String,
        @NotificationMode mode: Int,
        keywords: String
    ): Completable {
        return subscriptionTable().getSubscriptionForProfile(profileId, serviceId, url)
            .flatMapCompletable { entity: SubscriptionEntity ->
                val notificationsWereDisabled =
                    entity.notificationMode == NotificationMode.DISABLED
                val update = Completable.fromAction {
                    entity.notificationMode = mode
                    entity.notificationKeywords = keywords
                    subscriptionTable().update(entity)
                    recordSubscriptionUpsert(entity)
                }
                if (notificationsWereDisabled && mode != NotificationMode.DISABLED) {
                    // Notifications have just been enabled, mark all streams as "old".
                    update.andThen(rememberAllStreams(entity))
                } else {
                    update
                }
            }
    }

    fun updateFromInfo(info: FeedUpdateInfo) {
        val subscriptionEntity =
            subscriptionTable.getSubscriptionForProfile(profileId, info.uid)
                ?: return

        subscriptionEntity.name = info.name

        // some services do not provide an avatar URL
        info.avatarUrl?.let { subscriptionEntity.avatarUrl = it }

        // these two fields are null if the feed info was fetched using the fast feed method
        info.description?.let { subscriptionEntity.description = it }
        info.subscriberCount?.let { subscriptionEntity.subscriberCount = it }

        subscriptionTable.update(subscriptionEntity)
    }

    fun deleteSubscription(serviceId: Int, url: String): Completable {
        return Completable.fromCallable {
            var updatedSubscription: SubscriptionEntity? = null
            var deleted = false
            database.runInTransaction {
                val existing = subscriptionTable.getSubscriptionDirectForProfile(
                    profileId,
                    serviceId,
                    url
                )
                    ?: return@runInTransaction
                if (serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID) {
                    val remainingModes =
                        existing.youtubeModeMask and currentYoutubeModeMask.inv()
                    if (remainingModes != 0) {
                        existing.youtubeModeMask = remainingModes
                        subscriptionTable.update(existing)
                        updatedSubscription = existing
                        return@runInTransaction
                    }
                }
                deleted = subscriptionTable.deleteSubscriptionForProfile(
                    profileId,
                    serviceId,
                    url
                ) > 0
            }
            updatedSubscription?.let(::recordSubscriptionUpsert)
            if (deleted) {
                recordSubscriptionDelete(serviceId, url)
            }
            deleted
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun insertSubscription(subscriptionEntity: SubscriptionEntity) {
        val storedEntity = database.runInTransaction<SubscriptionEntity> {
            val url = requireNotNull(subscriptionEntity.url)
            subscriptionEntity.profileId = profileId
            val existing = subscriptionTable.getSubscriptionDirectForProfile(
                profileId,
                subscriptionEntity.serviceId,
                url
            )
            if (existing == null) {
                if (subscriptionEntity.serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID) {
                    subscriptionEntity.youtubeModeMask = currentYoutubeModeMask
                }
                subscriptionEntity.uid = subscriptionTable.insert(subscriptionEntity)
                subscriptionEntity
            } else {
                existing.name = subscriptionEntity.name
                existing.avatarUrl = subscriptionEntity.avatarUrl
                existing.subscriberCount = subscriptionEntity.subscriberCount
                existing.description = subscriptionEntity.description
                if (existing.serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID) {
                    existing.youtubeModeMask =
                        existing.youtubeModeMask or currentYoutubeModeMask
                }
                subscriptionTable.update(existing)
                existing
            }
        }
        recordSubscriptionUpsert(storedEntity)
    }

    fun deleteSubscription(subscriptionEntity: SubscriptionEntity) {
        subscriptionEntity.url?.let { url ->
            deleteSubscription(subscriptionEntity.serviceId, url).blockingAwait()
        }
    }

    fun isSubscribedInCurrentMode(subscriptionEntity: SubscriptionEntity): Boolean {
        return subscriptionEntity.serviceId != SubscriptionEntity.YOUTUBE_SERVICE_ID ||
            subscriptionEntity.youtubeModeMask and currentYoutubeModeMask != 0
    }

    /**
     * Fetches the list of videos for the provided channel and saves them in the database, so that
     * they will be considered as "old"/"already seen" streams and the user will never be notified
     * about any one of them.
     */
    private fun rememberAllStreams(subscription: SubscriptionEntity): Completable {
        return ExtractorHelper.getChannelInfo(subscription.serviceId, subscription.url, false)
            .flatMap { info ->
                ExtractorHelper.getChannelTab(subscription.serviceId, info.tabs.first(), false)
            }
            .map { channel -> channel.relatedItems.filterIsInstance<StreamInfoItem>().map { stream -> StreamEntity(stream) } }
            .flatMapCompletable { entities ->
                Completable.fromAction {
                    database.streamDAO().upsertAll(entities)
                }
            }.onErrorComplete()
    }

    private fun recordSubscriptionUpsert(subscription: SubscriptionEntity) {
        if (profileId != ProfileManager.DEFAULT_PROFILE_ID) {
            return
        }
        try {
            subscriptionSyncStore.recordLocalUpsert(subscription)
        } catch (error: Exception) {
            Log.e(TAG, "Could not journal a subscription addition", error)
        }
    }

    private fun recordSubscriptionDelete(serviceId: Int, url: String) {
        if (profileId != ProfileManager.DEFAULT_PROFILE_ID) {
            return
        }
        try {
            subscriptionSyncStore.recordLocalDelete(serviceId, url)
        } catch (error: Exception) {
            Log.e(TAG, "Could not journal a subscription deletion", error)
        }
    }

    companion object {
        private const val TAG = "SubscriptionManager"
    }
}
