package org.schabi.newpipe.settings.export

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType

class NewPipeDataMigrationManager(private val context: Context) {
    data class Preview(
        val historyItems: Int,
        val progressItems: Int,
        val playlists: Int,
        val playlistItems: Int,
        val compatibleSettings: Int
    ) {
        val hasHistory: Boolean
            get() = historyItems > 0 || progressItems > 0
        val hasPlaylists: Boolean
            get() = playlists > 0
        val hasCompatibleSettings: Boolean
            get() = compatibleSettings > 0
        val hasImportableData: Boolean
            get() = hasHistory || hasPlaylists || hasCompatibleSettings
    }

    data class Selection @JvmOverloads constructor(
        val importHistory: Boolean,
        val importPlaylists: Boolean,
        val importSettings: Boolean = false
    )

    data class Result(
        val historyItems: Int,
        val progressItems: Int,
        val playlists: Int,
        val playlistItems: Int,
        val compatibleSettings: Int,
        val skippedItems: Int
    )

    class UnsupportedSourceException(message: String) : Exception(message)

    fun inspect(
        databasePath: Path,
        sourcePreferences: Map<String, *> = emptyMap<String, Any>()
    ): Preview = openSource(databasePath).use { source ->
        val schema = inspectSchema(source)
        val compatibleSettings = CompatibleSettingsMigration(context).prepare(sourcePreferences)
        Preview(
            historyItems = if (schema.hasHistory) source.countRows(HISTORY_TABLE) else 0,
            progressItems = if (schema.hasProgress) source.countRows(STATE_TABLE) else 0,
            playlists = if (schema.hasPlaylists) source.countRows(PLAYLIST_TABLE) else 0,
            playlistItems = if (schema.hasPlaylists) source.countRows(PLAYLIST_JOIN_TABLE) else 0,
            compatibleSettings = compatibleSettings.size
        )
    }

    fun importData(
        databasePath: Path,
        selection: Selection,
        sourcePreferences: Map<String, *> = emptyMap<String, Any>()
    ): Result = openSource(databasePath).use { source ->
        val schema = inspectSchema(source)
        val streams = readStreams(source)
        val target = NewPipeDatabase.getInstance(context)
        val settingsMigration = CompatibleSettingsMigration(context)
        val compatibleSettings = settingsMigration.prepare(sourcePreferences)
        val settingsRollback = if (selection.importSettings && compatibleSettings.size > 0) {
            settingsMigration.apply(compatibleSettings)
        } else {
            null
        }
        var result = Result(0, 0, 0, 0, 0, 0)

        try {
            target.runInTransaction {
                val streamIds = mutableMapOf<Long, Long>()
                fun targetStreamId(sourceId: Long): Long? {
                    streamIds[sourceId]?.let { return it }
                    val stream = streams[sourceId] ?: return null
                    val streamDao = target.streamDAO()
                    val existing = streamDao.getStreamDirect(stream.serviceId, stream.url)
                    val targetId = existing?.uid ?: streamDao.insert(stream)
                    streamIds[sourceId] = targetId
                    return targetId
                }

                var historyItems = 0
                var progressItems = 0
                var playlists = 0
                var playlistItems = 0
                var skippedItems = 0

                if (selection.importHistory && schema.hasHistory) {
                    source.rawQuery(
                        "SELECT stream_id, access_date, repeat_count FROM $HISTORY_TABLE",
                        null
                    ).use { cursor ->
                        val writable = target.openHelper.writableDatabase
                        while (cursor.moveToNext()) {
                            val targetId = targetStreamId(cursor.getLong(0))
                            val accessDate = cursor.getLong(1)
                            if (targetId == null || accessDate <= 0) {
                                skippedItems++
                                continue
                            }
                            val repeatCount = cursor.getLong(2).coerceAtLeast(0)
                            writable.execSQL(
                                "INSERT OR IGNORE INTO $HISTORY_TABLE " +
                                    "(stream_id, access_date, repeat_count) VALUES (?, ?, ?)",
                                arrayOf(targetId, accessDate, repeatCount)
                            )
                            writable.execSQL(
                                "UPDATE $HISTORY_TABLE SET repeat_count = " +
                                    "MAX(repeat_count, ?) WHERE stream_id = ? AND access_date = ?",
                                arrayOf(repeatCount, targetId, accessDate)
                            )
                            historyItems++
                        }
                    }
                }

                if (selection.importHistory && schema.hasProgress) {
                    val existingStates = target.streamStateDAO().getAllDirect()
                        .associateBy { it.streamUid }
                    source.rawQuery(
                        "SELECT stream_id, progress_time FROM $STATE_TABLE",
                        null
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val targetId = targetStreamId(cursor.getLong(0))
                            if (targetId == null) {
                                skippedItems++
                                continue
                            }
                            val progress = cursor.getLong(1).coerceAtLeast(0)
                            val currentProgress = existingStates[targetId]?.progressMillis ?: -1
                            if (progress > currentProgress) {
                                target.streamStateDAO().upsert(
                                    StreamStateEntity(targetId, progress)
                                )
                                progressItems++
                            }
                        }
                    }
                }

                if (selection.importPlaylists && schema.hasPlaylists) {
                    val playlistDao = target.playlistDAO()
                    val playlistStreamDao = target.playlistStreamDAO()
                    val usedNames = playlistDao.getAllDirect()
                        .mapNotNullTo(mutableSetOf()) { it.name }
                    var displayIndex = playlistDao.getAllDirect()
                        .maxOfOrNull { it.displayIndex }?.plus(1) ?: 0L
                    val playlistOrder = when {
                        schema.usesAlphabeticalPlaylistOrder -> "name COLLATE NOCASE ASC, uid"
                        schema.playlistColumns.contains("display_index") -> "display_index, uid"
                        else -> "uid"
                    }

                    source.rawQuery(
                        "SELECT uid, name FROM $PLAYLIST_TABLE ORDER BY $playlistOrder",
                        null
                    ).use { playlistCursor ->
                        while (playlistCursor.moveToNext()) {
                            val sourcePlaylistId = playlistCursor.getLong(0)
                            val sourceName = playlistCursor.getString(1)?.trim().orEmpty()
                            val targetName = uniquePlaylistName(
                                sourceName.ifBlank { "Imported playlist" },
                                usedNames
                            )
                            val playlist = PlaylistEntity(
                                name = targetName,
                                isThumbnailPermanent = false,
                                thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                                displayIndex = displayIndex++
                            )
                            val targetPlaylistId = playlistDao.insert(playlist)
                            var targetIndex = 0
                            var firstStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID

                            source.rawQuery(
                                "SELECT stream_id FROM $PLAYLIST_JOIN_TABLE " +
                                    "WHERE playlist_id = ? ORDER BY join_index",
                                arrayOf(sourcePlaylistId.toString())
                            ).use { itemCursor ->
                                while (itemCursor.moveToNext()) {
                                    val targetId = targetStreamId(itemCursor.getLong(0))
                                    if (targetId == null) {
                                        skippedItems++
                                        continue
                                    }
                                    if (firstStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                                        firstStreamId = targetId
                                    }
                                    playlistStreamDao.insert(
                                        PlaylistStreamEntity(
                                            targetPlaylistId,
                                            targetId,
                                            targetIndex++
                                        )
                                    )
                                    playlistItems++
                                }
                            }
                            if (firstStreamId != PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                                playlist.thumbnailStreamId = firstStreamId
                                playlist.uid = targetPlaylistId
                                playlistDao.update(playlist)
                            }
                            playlists++
                        }
                    }
                }

                result = Result(
                    historyItems,
                    progressItems,
                    playlists,
                    playlistItems,
                    if (selection.importSettings) compatibleSettings.size else 0,
                    skippedItems
                )
            }
        } catch (error: Throwable) {
            if (settingsRollback != null) {
                try {
                    settingsMigration.rollback(settingsRollback)
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                }
            }
            throw error
        }
        result
    }

    private fun openSource(databasePath: Path): SQLiteDatabase = SQLiteDatabase.openDatabase(
        databasePath.toString(),
        null,
        SQLiteDatabase.OPEN_READONLY
    )

    private fun inspectSchema(source: SQLiteDatabase): SourceSchema {
        val streamColumns = source.columnsOf(STREAM_TABLE)
        if (!streamColumns.containsAll(REQUIRED_STREAM_COLUMNS)) {
            throw UnsupportedSourceException(
                "The source database does not contain a compatible NewPipe streams table"
            )
        }
        val historyColumns = source.columnsOf(HISTORY_TABLE)
        val stateColumns = source.columnsOf(STATE_TABLE)
        val playlistColumns = source.columnsOf(PLAYLIST_TABLE)
        val joinColumns = source.columnsOf(PLAYLIST_JOIN_TABLE)
        val schema = SourceSchema(
            hasHistory = historyColumns.containsAll(REQUIRED_HISTORY_COLUMNS),
            hasProgress = stateColumns.containsAll(REQUIRED_STATE_COLUMNS),
            hasPlaylists = playlistColumns.containsAll(REQUIRED_PLAYLIST_COLUMNS) &&
                joinColumns.containsAll(REQUIRED_PLAYLIST_JOIN_COLUMNS),
            playlistColumns = playlistColumns,
            usesAlphabeticalPlaylistOrder =
                source.tableExists(PIPEPIPE_SPONSORBLOCK_WHITELIST_TABLE) ||
                    source.userVersion() >= PIPEPIPE_DATABASE_VERSION_FLOOR
        )
        if (!schema.hasHistory && !schema.hasProgress && !schema.hasPlaylists) {
            throw UnsupportedSourceException(
                "The source database does not contain compatible history or playlist data"
            )
        }
        return schema
    }

    private fun readStreams(source: SQLiteDatabase): Map<Long, StreamEntity> {
        val result = mutableMapOf<Long, StreamEntity>()
        source.rawQuery("SELECT * FROM $STREAM_TABLE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val uid = cursor.long("uid") ?: continue
                val serviceId = cursor.long("service_id")?.toInt() ?: continue
                val url = cursor.string("url") ?: continue
                val streamType = cursor.string("stream_type")
                    ?.let { runCatching { StreamType.valueOf(it) }.getOrNull() }
                    ?: continue
                result[uid] = StreamEntity(
                    serviceId = serviceId,
                    url = url,
                    title = cursor.string("title").orEmpty(),
                    streamType = streamType,
                    duration = cursor.long("duration") ?: -1,
                    uploader = cursor.string("uploader").orEmpty(),
                    uploaderUrl = cursor.string("uploader_url"),
                    thumbnailUrl = cursor.string("thumbnail_url"),
                    viewCount = cursor.long("view_count"),
                    textualUploadDate = cursor.string("textual_upload_date"),
                    uploadDate = cursor.long("upload_date")?.toOffsetDateTime(),
                    isUploadDateApproximation = cursor.long("is_upload_date_approximation")
                        ?.let { it != 0L },
                    uploaderAvatarUrl = cursor.string("uploader_avatar_url"),
                    requiresMembership = cursor.long("requires_membership") == 1L
                )
            }
        }
        return result
    }

    private fun uniquePlaylistName(sourceName: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(sourceName)) {
            return sourceName
        }
        var suffix = 1
        while (true) {
            val candidate = if (suffix == 1) {
                "$sourceName (Imported)"
            } else {
                "$sourceName (Imported $suffix)"
            }
            if (usedNames.add(candidate)) {
                return candidate
            }
            suffix++
        }
    }

    private fun SQLiteDatabase.columnsOf(table: String): Set<String> {
        if (!tableExists(table)) {
            return emptySet()
        }
        return rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }
    }

    private fun SQLiteDatabase.tableExists(table: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(table)
    ).use { it.moveToFirst() }

    private fun SQLiteDatabase.countRows(table: String): Int = rawQuery(
        "SELECT COUNT(*) FROM `$table`",
        null
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            0
        } else {
            cursor.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun SQLiteDatabase.userVersion(): Int = rawQuery(
        "PRAGMA user_version",
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.long(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(this),
        ZoneOffset.UTC
    )

    private data class SourceSchema(
        val hasHistory: Boolean,
        val hasProgress: Boolean,
        val hasPlaylists: Boolean,
        val playlistColumns: Set<String>,
        val usesAlphabeticalPlaylistOrder: Boolean
    )

    companion object {
        private const val STREAM_TABLE = "streams"
        private const val HISTORY_TABLE = "stream_history"
        private const val STATE_TABLE = "stream_state"
        private const val PLAYLIST_TABLE = "playlists"
        private const val PLAYLIST_JOIN_TABLE = "playlist_stream_join"
        private const val PIPEPIPE_SPONSORBLOCK_WHITELIST_TABLE = "sponsorblock_whitelist"
        private const val PIPEPIPE_DATABASE_VERSION_FLOOR = 900

        private val REQUIRED_STREAM_COLUMNS = setOf(
            "uid",
            "service_id",
            "url",
            "title",
            "stream_type",
            "duration",
            "uploader"
        )
        private val REQUIRED_HISTORY_COLUMNS = setOf(
            "stream_id",
            "access_date",
            "repeat_count"
        )
        private val REQUIRED_STATE_COLUMNS = setOf("stream_id", "progress_time")
        private val REQUIRED_PLAYLIST_COLUMNS = setOf("uid", "name")
        private val REQUIRED_PLAYLIST_JOIN_COLUMNS = setOf(
            "playlist_id",
            "stream_id",
            "join_index"
        )
    }
}
