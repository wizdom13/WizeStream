package org.schabi.newpipe.learning

import android.content.Context
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.util.ExtractorHelper

/** Resolves an explicit course before opening a dashboard lesson. */
object LearningCourseLauncher {
    fun sources(context: Context, streamId: Long, profileId: String): Single<List<LearningContentSourceEntity>> = Single.fromCallable {
        NewPipeDatabase.getInstance(context).learningContentDAO().coursesForStream(streamId, profileId)
    }.subscribeOn(Schedulers.io())

    fun queue(context: Context, stream: StreamEntity, profileId: String, source: LearningContentSourceEntity?): Single<PlayQueue> {
        val result: Single<PlayQueue> = when {
            source == null -> Single.just(LocalMediaPlayQueue(listOf(stream.toPlayQueueItem()), 0))

            source.localPlaylistId != null -> Single.fromCallable {
                val streams = NewPipeDatabase.getInstance(context).learningContentDAO()
                    .courseStreams(source.localPlaylistId, profileId)
                val index = streams.indexOfFirst { it.serviceId == stream.serviceId && it.url == stream.url }
                check(index >= 0) { "Lesson is no longer in this course" }
                LocalMediaPlayQueue(streams.map { it.toPlayQueueItem() }, index)
            }

            else -> ExtractorHelper.getPlaylistInfo(requireNotNull(source.serviceId), requireNotNull(source.url), false)
                .flatMap { info -> remoteQueue(info.serviceId, requireNotNull(source.url), stream, info.relatedItems, info.nextPage, mutableSetOf()) }
        }
        return result.map { queue ->
            if (source != null) {
                queue.learningPlaylistContext = LearningPlaylistContext(source.sourceId, source.title.orEmpty(), profileId)
            }
            queue
        }.subscribeOn(Schedulers.io())
    }

    private fun remoteQueue(
        serviceId: Int,
        url: String,
        selected: StreamEntity,
        streams: List<StreamInfoItem>,
        nextPage: Page?,
        visited: MutableSet<String>
    ): Single<PlayQueue> = Single.defer {
        val index = streams.indexOfFirst { it.serviceId == selected.serviceId && it.url == selected.url }
        if (index >= 0) {
            Single.just(PlaylistPlayQueue(serviceId, url, nextPage, streams, index))
        } else if (!Page.isValid(nextPage) || !visited.add(listOf(nextPage?.url, nextPage?.id, nextPage?.ids, nextPage?.cookies, nextPage?.body?.contentHashCode()).toString())) {
            Single.error(IllegalStateException("Lesson is no longer in this course"))
        } else {
            ExtractorHelper.getMorePlaylistItems(serviceId, url, nextPage)
                .flatMap { page -> remoteQueue(serviceId, url, selected, streams + page.items, page.nextPage, visited) }
        }
    }
}
