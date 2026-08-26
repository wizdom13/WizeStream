package org.schabi.newpipe.local.feed

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class SavedSearchFeedManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context.applicationContext)
    private val savedSearchFeedDao = database.savedSearchFeedDAO()
    private val streamDao = database.streamDAO()

    fun getAll(): Single<List<SavedSearchFeedEntity>> =
        Single.fromCallable(savedSearchFeedDao::getAllDirect)
            .subscribeOn(Schedulers.io())

    fun create(
        name: String,
        serviceId: Int,
        query: String,
        contentFilters: Array<String>,
        sortFilters: IntArray
    ): Single<Long> = Single.fromCallable {
        savedSearchFeedDao.insert(
            SavedSearchFeedEntity(
                name = name.trim(),
                serviceId = serviceId,
                query = query.trim(),
                contentFilter = SavedSearchFeedEntity.encodeContentFilters(contentFilters),
                sortFilter = SavedSearchFeedEntity.encodeSortFilters(sortFilters)
            )
        )
    }.subscribeOn(Schedulers.io())

    fun delete(feedId: Long): Completable = Completable.fromAction {
        database.runInTransaction {
            savedSearchFeedDao.delete(feedId)
            streamDao.deleteOrphans()
        }
    }.subscribeOn(Schedulers.io())

    fun getCachedItems(feedId: Long): Single<List<StreamInfoItem>> =
        Single.fromCallable {
            savedSearchFeedDao.getCachedStreamsDirect(feedId, MAXIMUM_CACHED_ITEMS)
                .map(StreamEntity::toStreamInfoItem)
        }.subscribeOn(Schedulers.io())

    fun replaceCache(feedId: Long, items: List<InfoItem>): Completable =
        cacheItems(feedId, items, replace = true)

    fun appendCache(feedId: Long, items: List<InfoItem>): Completable =
        cacheItems(feedId, items, replace = false)

    private fun cacheItems(
        feedId: Long,
        items: List<InfoItem>,
        replace: Boolean
    ): Completable = Completable.fromAction {
        val streamItems = items.filterIsInstance<StreamInfoItem>()
            .distinctBy { item -> item.serviceId to item.url }
            .take(MAXIMUM_CACHED_ITEMS)
        if (streamItems.isEmpty() && !replace) {
            return@fromAction
        }

        database.runInTransaction {
            if (replace) {
                savedSearchFeedDao.clearCachedStreams(feedId)
            }

            val positionOffset = if (replace) {
                0L
            } else {
                savedSearchFeedDao.nextCachePosition(feedId)
            }
            val streamIds = streamDao.upsertAll(streamItems.map(::StreamEntity))
            val cacheEntities = streamIds.mapIndexed { index, streamId ->
                SavedSearchFeedStreamEntity(feedId, streamId, positionOffset + index)
            }
            savedSearchFeedDao.insertCachedStreams(cacheEntities)
            savedSearchFeedDao.pruneCache(feedId, MAXIMUM_CACHED_ITEMS)

            if (replace) {
                savedSearchFeedDao.setLastRefresh(feedId, OffsetDateTime.now(ZoneOffset.UTC))
            }
        }
    }.subscribeOn(Schedulers.io())

    companion object {
        const val NO_SAVED_SEARCH_FEED = -1L
        const val MAXIMUM_CACHED_ITEMS = 300
    }
}
