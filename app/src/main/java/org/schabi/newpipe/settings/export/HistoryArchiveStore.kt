package org.schabi.newpipe.settings.export

import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

internal class HistoryArchiveStore(
    private val database: AppDatabase,
    private val profileId: String,
    private val recordWatch: (Long, Long, Long) -> Unit = { _, _, _ -> },
    private val recordSearch: (Int, String, Long) -> Unit = { _, _, _ -> }
) {
    fun export(): HistoryArchive = database.runInTransaction(
        Callable {
            HistoryArchive(
                watches = database.streamHistoryDAO().getAllDirectForProfile(profileId).map { event ->
                    val stream = requireNotNull(database.streamDAO().getStreamDirect(event.streamUid))
                    ArchivedWatch(
                        stream.serviceId, stream.url, stream.title, stream.streamType.name, stream.duration, stream.uploader,
                        event.accessDate.toInstant().toEpochMilli(), event.repeatCount, stream.thumbnailUrl, stream.sourceType, stream.mimeType
                    )
                },
                searches = database.searchHistoryDAO().getAllDirect().mapNotNull { event ->
                    val date = event.creationDate ?: return@mapNotNull null
                    val query = event.search ?: return@mapNotNull null
                    ArchivedSearch(event.serviceId, query, date.toInstant().toEpochMilli())
                }
            )
        }
    )

    /** Imports into the explicitly selected profile, leaving all existing history intact. */
    fun merge(archive: HistoryArchive): Int {
        HistoryArchiveCodec.validate(archive)
        return database.runInTransaction(
            Callable {
                var added = 0
                archive.watches.forEach { event ->
                    check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                    val streamId = database.streamDAO().getStreamDirect(event.serviceId, event.url)?.uid
                        ?: database.streamDAO().insert(
                            StreamEntity(
                                serviceId = event.serviceId, url = event.url, title = event.title,
                                streamType = StreamType.valueOf(event.streamType), duration = event.duration, uploader = event.uploader,
                                thumbnailUrl = event.thumbnailUrl, sourceType = event.sourceType, mimeType = event.mimeType
                            )
                        )
                    val date = Instant.ofEpochMilli(event.timestamp).atOffset(ZoneOffset.UTC)
                    if (!database.streamHistoryDAO().hasTakeoutEventForProfile(profileId, streamId, date)) {
                        recordWatch(streamId, event.timestamp, event.repeatCount)
                        database.streamHistoryDAO().insert(StreamHistoryEntity(streamId, date, event.repeatCount, profileId))
                        added++
                    }
                }
                archive.searches.forEach { event ->
                    check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
                    val date = Instant.ofEpochMilli(event.timestamp).atOffset(ZoneOffset.UTC)
                    if (!database.searchHistoryDAO().hasTakeoutEvent(event.serviceId, event.query, date)) {
                        recordSearch(event.serviceId, event.query, event.timestamp)
                        database.searchHistoryDAO().insert(SearchHistoryEntry(date, event.serviceId, event.query))
                        added++
                    }
                }
                added
            }
        )
    }
}
