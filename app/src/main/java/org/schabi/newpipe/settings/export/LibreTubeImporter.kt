package org.schabi.newpipe.settings.export

import java.time.Instant
import java.time.ZoneOffset
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon

internal enum class LibreTubeCategory { SUBSCRIPTIONS, HISTORY, POSITIONS, PLAYLISTS, BOOKMARKS }
internal data class LibreTubeImportResult(val added: Int, val skipped: Int)

internal class LibreTubeImporter(
    private val database: AppDatabase,
    private val profileId: String,
    private val recordWatch: (Long, Long) -> Unit = { _, _ -> },
    private val recordProgress: (Long, Long, Long) -> Unit = { _, _, _ -> },
    private val recordSubscription: (SubscriptionEntity) -> Unit = {},
    private val checkActive: () -> Unit = {}
) {
    fun import(data: LibreTubeBackup, selected: Set<LibreTubeCategory>, now: Long): LibreTubeImportResult {
        var added = 0
        var skipped = data.skipped
        database.runInTransaction {
            checkActive()
            fun stream(video: LibreTubeVideo): Long {
                checkActive()
                return database.streamDAO().getStreamDirect(0, video.url)?.uid ?: database.streamDAO().insert(
                    StreamEntity(
                        serviceId = 0,
                        url = video.url,
                        title = video.title,
                        streamType = StreamType.VIDEO_STREAM,
                        duration = video.duration,
                        uploader = video.uploader,
                        thumbnailUrl = video.thumbnail
                    )
                )
            }
            if (LibreTubeCategory.SUBSCRIPTIONS in selected) {
                val ids = mutableMapOf<String, Long>()
                // Group-only exports still identify their subscribed channels.
                val channels = (data.subscriptions + data.groups.flatMap { group -> group.channels.map { LibreTubeChannel(it, it) } }).distinctBy { it.id }
                for (channel in channels) {
                    checkActive()
                    val existing = database.subscriptionDAO().getSubscriptionDirectForProfile(profileId, 0, channel.url)
                    if (existing != null) {
                        ids[channel.id] = existing.uid
                        skipped++
                        continue
                    }
                    val entity = SubscriptionEntity(serviceId = 0, url = channel.url, name = channel.name, profileId = profileId)
                    entity.uid = database.subscriptionDAO().insert(entity)
                    recordSubscription(entity)
                    ids[channel.id] = entity.uid
                    added++
                }
                val groups = database.feedGroupDAO().getAllDirectForProfile(profileId).toMutableList()
                for (group in data.groups) {
                    checkActive()
                    val existing = groups.firstOrNull { it.name == group.name }
                    val id = existing?.uid ?: run {
                        val entity = FeedGroupEntity(uid = 0, name = group.name, icon = FeedGroupIcon.ALL, profileId = profileId)
                        val uid = database.feedGroupDAO().insert(entity)
                        groups.add(entity.copy(uid = uid))
                        added++
                        uid
                    }
                    database.feedGroupDAO().insertSubscriptionsToGroup(group.channels.mapNotNull { channel -> ids[channel]?.let { FeedGroupSubscriptionEntity(id, it) } })
                }
            }
            if (LibreTubeCategory.HISTORY in selected) {
                val watched = database.streamHistoryDAO().getAllDirectForProfile(profileId).map { it.streamUid }.toMutableSet()
                for (video in data.history) {
                    val id = stream(video)
                    if (!watched.add(id)) {
                        skipped++
                        continue
                    }
                    recordWatch(id, now)
                    database.streamHistoryDAO().insert(StreamHistoryEntity(id, Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC), 1, profileId))
                    added++
                }
            }
            if (LibreTubeCategory.POSITIONS in selected) {
                val states = database.streamStateDAO().getAllDirectForProfile(profileId).map { it.streamUid }.toMutableSet()
                val metadata = (data.history + data.playlists.flatMap { it.videos }).associateBy { it.id }
                for ((videoId, position) in data.positions) {
                    val id = stream(metadata[videoId] ?: LibreTubeVideo(videoId, videoId))
                    if (!states.add(id)) {
                        skipped++
                        continue
                    }
                    recordProgress(id, position, now)
                    database.streamStateDAO().upsert(StreamStateEntity(id, position, profileId))
                    added++
                }
            }
            if (LibreTubeCategory.PLAYLISTS in selected) {
                val playlists = database.playlistDAO().getAllDirectForProfile(profileId).toMutableList()
                val used = mutableSetOf<Long>()
                for (playlist in data.playlists) {
                    checkActive()
                    val base = "${playlist.name} (LibreTube)"
                    val pattern = Regex("${Regex.escape(playlist.name)} \\(LibreTube(?: [0-9]+)?\\)")
                    val urls = playlist.videos.map { it.url }
                    val duplicate = playlists.firstOrNull { existing ->
                        existing.uid !in used && existing.name?.matches(pattern) == true &&
                            database.playlistStreamDAO().getOrderedStreamsDirectForProfile(profileId, existing.uid).map { it.url } == urls
                    }
                    if (duplicate != null) {
                        used.add(duplicate.uid)
                        skipped++
                        continue
                    }
                    var name = base
                    var suffix = 2
                    while (playlists.any { it.name == name }) name = "${playlist.name} (LibreTube ${suffix++})"
                    val ids = playlist.videos.map(::stream)
                    val entity = PlaylistEntity(
                        name = name,
                        isThumbnailPermanent = false,
                        thumbnailStreamId = ids.firstOrNull() ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                        displayIndex = (playlists.maxOfOrNull { it.displayIndex } ?: -1) + 1,
                        profileId = profileId
                    )
                    entity.uid = database.playlistDAO().insert(entity)
                    playlists.add(entity)
                    used.add(entity.uid)
                    ids.forEachIndexed { index, id -> database.playlistStreamDAO().insert(PlaylistStreamEntity(entity.uid, id, index)) }
                    added++
                }
            }
            if (LibreTubeCategory.BOOKMARKS in selected) {
                val existing = database.playlistRemoteDAO().getAllDirectForProfile(profileId).filter { it.serviceId == 0 }.map { it.url }.toMutableSet()
                for (bookmark in data.bookmarks) {
                    checkActive()
                    val url = "https://www.youtube.com/playlist?list=${bookmark.id}"
                    if (!existing.add(url)) {
                        skipped++
                        continue
                    }
                    database.playlistRemoteDAO().insert(
                        PlaylistRemoteEntity(
                            serviceId = 0,
                            orderingName = bookmark.name,
                            url = url,
                            thumbnailUrl = null,
                            uploader = null,
                            streamCount = bookmark.count,
                            profileId = profileId
                        )
                    )
                    added++
                }
            }
            checkActive()
        }
        return LibreTubeImportResult(added, skipped)
    }
}
