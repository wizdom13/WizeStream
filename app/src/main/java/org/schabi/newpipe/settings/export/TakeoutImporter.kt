package org.schabi.newpipe.settings.export

import java.time.Instant
import java.time.ZoneOffset
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType

internal data class TakeoutImportResult(
    val playlists: Int,
    val videos: Int,
    val watches: Int,
    val skipped: Int,
    val bookmarks: Int = 0,
    val subscriptions: Int = 0,
    val searches: Int = 0
)

internal class TakeoutImporter(
    private val database: AppDatabase,
    private val recordSearch: (String, Long) -> Unit = { _, _ -> },
    private val recordSubscription: (SubscriptionEntity) -> Unit = {},
    private val recordWatch: (Long, Long, Long) -> Unit
) {
    fun import(data: TakeoutData): TakeoutImportResult {
        var playlists = 0
        var videos = 0
        var watches = 0
        var bookmarks = 0
        var subscriptions = 0
        var searches = 0
        var skipped = data.skipped
        database.runInTransaction {
            fun stream(video: TakeoutVideo): Long = database.streamDAO().getStreamDirect(0, video.url)?.uid ?: database.streamDAO().insert(
                StreamEntity(serviceId = 0, url = video.url, title = video.title, streamType = StreamType.VIDEO_STREAM, duration = -1, uploader = "")
            )
            val existing = database.playlistDAO().getAllDirect().toMutableList()
            val used = mutableSetOf<Long>()
            val bookmarksByUrl = database.playlistRemoteDAO().getAllDirect().filter { it.serviceId == 0 }.map { it.url }.toMutableSet()
            for (playlist in data.playlists) {
                check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                val base = playlist.name.take(200)
                val pattern = Regex("${Regex.escape(base)} \\(Takeout(?: [2-9][0-9]*| 1[0-9]+)?\\)")
                val candidates = existing.filter { it.uid !in used && it.name?.matches(pattern) == true }
                val ordered = candidates.associateWith { database.playlistStreamDAO().getOrderedStreamsDirect(it.uid) }
                val urls = playlist.videos.map { it.url }
                var target = candidates.firstOrNull { ordered[it]?.map { video -> video.url } == urls } ?: candidates.firstOrNull()
                val old = ordered[target].orEmpty()
                if (target == null) {
                    var name = "$base (Takeout)"
                    var suffix = 2
                    while (existing.any { it.name == name }) name = "$base (Takeout ${suffix++})"
                    target = PlaylistEntity(name = name, isThumbnailPermanent = false, thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID, displayIndex = -1)
                    target.uid = database.playlistDAO().insert(target)
                    existing.add(target)
                    playlists++
                }
                used.add(target.uid)
                // Count occurrences so repeated videos survive, while importing the same file stays idempotent.
                val remaining = old.groupingBy { it.url }.eachCount().toMutableMap()
                var index = database.playlistStreamDAO().getMaximumIndexDirect(target.uid) + 1
                for (video in playlist.videos) {
                    check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                    val occurrences = remaining[video.url] ?: 0
                    if (occurrences > 0) {
                        remaining[video.url] = occurrences - 1
                        skipped++
                        continue
                    }
                    val streamId = stream(video)
                    database.playlistStreamDAO().insert(PlaylistStreamEntity(target.uid, streamId, index++))
                    if (target.thumbnailStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                        target.thumbnailStreamId = streamId
                        database.playlistDAO().update(target)
                    }
                    videos++
                }
                if (playlist.url != null && bookmarksByUrl.add(playlist.url)) {
                    database.playlistRemoteDAO().insert(
                        PlaylistRemoteEntity(serviceId = 0, orderingName = playlist.name, url = playlist.url, thumbnailUrl = null, uploader = null, streamCount = playlist.videos.size.toLong())
                    )
                    bookmarks++
                }
            }
            for (subscription in data.subscriptions) {
                check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                if (database.subscriptionDAO().getSubscriptionDirect(0, subscription.url) != null) {
                    skipped++
                    continue
                }
                val entity = SubscriptionEntity(serviceId = 0, url = subscription.url, name = subscription.name)
                entity.uid = database.subscriptionDAO().insert(entity)
                recordSubscription(entity)
                subscriptions++
            }
            for (watch in data.history) {
                check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                val streamId = stream(watch.video)
                val date = Instant.ofEpochMilli(watch.timestamp).atOffset(ZoneOffset.UTC)
                if (database.streamHistoryDAO().hasTakeoutEvent(streamId, date)) {
                    skipped++
                } else {
                    // Initialize and journal before materializing, as normal playback does.
                    recordWatch(streamId, watch.timestamp, 1)
                    database.streamHistoryDAO().insert(StreamHistoryEntity(streamId, date, 1))
                    watches++
                }
            }
            for (search in data.searches) {
                check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                val date = Instant.ofEpochMilli(search.timestamp).atOffset(ZoneOffset.UTC)
                if (database.searchHistoryDAO().hasTakeoutEvent(0, search.query, date)) {
                    skipped++
                } else {
                    recordSearch(search.query, search.timestamp)
                    database.searchHistoryDAO().insert(SearchHistoryEntry(date, 0, search.query))
                    searches++
                }
            }
        }
        return TakeoutImportResult(playlists, videos, watches, skipped, bookmarks, subscriptions, searches)
    }
}
