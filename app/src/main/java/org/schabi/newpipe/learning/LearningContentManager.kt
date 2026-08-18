/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import android.content.Context
import androidx.room.ColumnInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningContentStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

data class LearningContentKey(
    @ColumnInfo(name = "service_id") val serviceId: Int,
    @ColumnInfo(name = "url") val url: String
)

class LearningContentManager private constructor(context: Context) {
    private val database = NewPipeDatabase.getInstance(context.applicationContext)
    private val dao = database.learningContentDAO()
    private val disposables = CompositeDisposable()

    @Volatile
    private var eligibleStreamKeys: Set<String> = emptySet()

    @Volatile
    private var markedSourceIds: Set<String> = emptySet()

    init {
        disposables.add(
            dao.observeEligibleStreamKeys()
                .subscribeOn(Schedulers.io())
                .subscribe { keys ->
                    eligibleStreamKeys = keys.mapTo(mutableSetOf()) {
                        streamKey(it.serviceId, it.url)
                    }
                }
        )
        disposables.add(
            dao.observeSourceIds()
                .subscribeOn(Schedulers.io())
                .subscribe { ids ->
                    markedSourceIds = ids.toSet()
                }
        )
    }

    fun isStreamLearning(serviceId: Int, url: String): Boolean = eligibleStreamKeys.contains(streamKey(serviceId, url))

    fun isStreamSourceMarked(serviceId: Int, url: String): Boolean = markedSourceIds.contains(streamSourceId(serviceId, url))

    fun isLocalPlaylistMarked(playlistId: Long): Boolean = markedSourceIds.contains(localPlaylistSourceId(playlistId))

    fun isRemotePlaylistMarked(serviceId: Int, url: String): Boolean = markedSourceIds.contains(remotePlaylistSourceId(serviceId, url))

    fun setStreamMarked(stream: StreamEntity, marked: Boolean): Completable = databaseAction {
        val sourceId = streamSourceId(stream.serviceId, stream.url)
        if (marked) {
            val streamId = database.streamDAO().upsert(stream)
            dao.upsertSource(
                LearningContentSourceEntity(
                    sourceId = sourceId,
                    sourceType = LearningContentSourceEntity.TYPE_STREAM,
                    serviceId = stream.serviceId,
                    url = stream.url,
                    title = stream.title,
                    thumbnailUrl = stream.thumbnailUrl
                )
            )
            dao.updateSourceMetadata(sourceId, stream.title, stream.thumbnailUrl)
            dao.insertSourceStreams(listOf(LearningContentStreamEntity(sourceId, streamId)))
            dao.markSessionsDesignated(listOf(streamId))
        } else {
            dao.deleteSource(sourceId)
        }
    }

    fun setLocalPlaylistMarked(playlistId: Long, title: String, marked: Boolean): Completable = databaseAction {
        val sourceId = localPlaylistSourceId(playlistId)
        if (marked) {
            dao.upsertSource(
                LearningContentSourceEntity(
                    sourceId = sourceId,
                    sourceType = LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST,
                    localPlaylistId = playlistId,
                    title = title
                )
            )
            dao.updateSourceMetadata(sourceId, title, null)
            dao.markLocalPlaylistSessionsDesignated(playlistId)
        } else {
            dao.deleteSource(sourceId)
        }
    }

    fun setRemotePlaylistMarked(
        serviceId: Int,
        url: String,
        title: String,
        thumbnailUrl: String?,
        streams: List<StreamEntity>,
        marked: Boolean
    ): Completable = databaseAction {
        val sourceId = remotePlaylistSourceId(serviceId, url)
        if (marked) {
            dao.upsertSource(
                LearningContentSourceEntity(
                    sourceId = sourceId,
                    sourceType = LearningContentSourceEntity.TYPE_REMOTE_PLAYLIST,
                    serviceId = serviceId,
                    url = url,
                    title = title,
                    thumbnailUrl = thumbnailUrl
                )
            )
            dao.updateSourceMetadata(sourceId, title, thumbnailUrl)
            indexRemoteStreams(sourceId, streams)
        } else {
            dao.deleteSource(sourceId)
        }
    }

    fun addRemotePlaylistStreams(
        serviceId: Int,
        url: String,
        streams: List<StreamEntity>
    ): Completable = databaseAction {
        val sourceId = remotePlaylistSourceId(serviceId, url)
        if (dao.isSourceMarked(sourceId)) {
            indexRemoteStreams(sourceId, streams)
        }
    }

    private fun indexRemoteStreams(sourceId: String, streams: List<StreamEntity>) {
        if (streams.isEmpty()) return
        val streamIds = database.streamDAO().upsertAll(streams)
        dao.insertSourceStreams(streamIds.map { LearningContentStreamEntity(sourceId, it) })
        dao.markSessionsDesignated(streamIds)
    }

    private fun databaseAction(action: () -> Unit): Completable = Completable.fromAction {
        database.runInTransaction { action() }
        eligibleStreamKeys = dao.getEligibleStreamKeysDirect().mapTo(mutableSetOf()) {
            streamKey(it.serviceId, it.url)
        }
        markedSourceIds = dao.getSourceIdsDirect().toSet()
    }.subscribeOn(Schedulers.io())

    companion object {
        @Volatile
        private var instance: LearningContentManager? = null

        @JvmStatic
        fun getInstance(context: Context): LearningContentManager = instance ?: synchronized(this) {
            instance ?: LearningContentManager(context).also { instance = it }
        }

        @JvmStatic
        fun streamSourceId(serviceId: Int, url: String) = "stream:$serviceId:$url"

        @JvmStatic
        fun localPlaylistSourceId(playlistId: Long) = "local-playlist:$playlistId"

        @JvmStatic
        fun remotePlaylistSourceId(serviceId: Int, url: String) = "remote-playlist:$serviceId:$url"

        private fun streamKey(serviceId: Int, url: String) = "$serviceId\n$url"
    }
}
