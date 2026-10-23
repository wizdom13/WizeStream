package org.schabi.newpipe.profiles

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.sync.HistorySyncCategory
import org.schabi.newpipe.sync.HistorySyncRecorder
import org.schabi.newpipe.sync.RoomHistorySyncStore

/** Copies profile-owned data in one Room transaction. The source is never modified. */
object ProfileTransfer {
    private val tables = listOf(
        "subscriptions", "playlists", "remote_playlists", "feed_group", "saved_search_feed",
        "stream_history", "stream_state", "search_history", "learning_sessions"
    )

    @JvmStatic
    fun preview(context: Context, source: String, destination: String): Single<List<Int>> = Single.fromCallable {
        validate(context, source, destination)
        val db = NewPipeDatabase.getInstance(context).openHelper.readableDatabase
        listOf(source, destination).flatMap { profile ->
            tables.map { table ->
                db.query("SELECT COUNT(*) FROM $table WHERE profile_id = ?", arrayOf(profile)).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
            }
        }
    }.subscribeOn(Schedulers.io())

    @JvmStatic
    fun copy(context: Context, source: String, destination: String, replace: Boolean): Single<Boolean> = Single.fromCallable {
        run {
            validate(context, source, destination)
            val database = NewPipeDatabase.getInstance(context)
            database.runInTransaction {
                validate(context, source, destination)
                val db = database.openHelper.writableDatabase
                val recorder = HistorySyncRecorder.get(context)
                val historyStore = RoomHistorySyncStore.get(context)
                historyStore.reconcileLocal(HistorySyncCategory.WATCH)
                historyStore.reconcileLocal(HistorySyncCategory.SEARCH)
                if (replace) {
                    recorder.recordWatchAllDeleteForProfile(destination)
                    recorder.recordProgressAllDeleteForProfile(destination)
                    recorder.recordSearchAllDeleteForProfile(destination)
                    tables.reversed().forEach { db.delete(it, "profile_id = ?", arrayOf(destination)) }
                }
                val subscriptions = copyRows(db, "subscriptions", source, destination, listOf("service_id", "url"))
                val playlists = copyRows(db, "playlists", source, destination, listOf("name"))
                copyRows(db, "remote_playlists", source, destination, listOf("service_id", "url"))
                val groups = copyRows(db, "feed_group", source, destination, listOf("name"))
                val searches = copyRows(
                    db,
                    "saved_search_feed",
                    source,
                    destination,
                    listOf("service_id", "query", "content_filter", "sort_filter")
                )
                copyLinks(db, "feed", "subscription_id", subscriptions)
                copyLinks(db, "feed_last_updated", "subscription_id", subscriptions)
                copyLinks(db, "feed_group_subscription_join", "group_id", groups, "subscription_id", subscriptions)
                copyPlaylistItems(db, playlists)
                copyLearningSources(db, playlists)
                copyLinks(db, "saved_search_feed_stream", "feed_id", searches)
                copyHistory(db, source, destination, recorder)
                rows(db, "learning_sessions", "profile_id = ?", arrayOf(source)).forEach { row ->
                    if (rows(
                            db,
                            "learning_sessions",
                            "profile_id = ? AND stream_id = ? AND started_at = ? AND ended_at = ?",
                            arrayOf(destination, row["stream_id"], row["started_at"], row["ended_at"])
                        ).isNotEmpty()
                    ) {
                        return@forEach
                    }
                    row.put("profile_id", destination)
                    row.put(
                        "session_id",
                        UUID.nameUUIDFromBytes(
                            (destination + ":" + row.getAsString("session_id")).toByteArray(Charsets.UTF_8)
                        ).toString()
                    )
                    db.insert("learning_sessions", SQLiteDatabase.CONFLICT_IGNORE, row)
                }
                ProfileTransferPreferences.copy(context, source, destination, replace, playlists)
            }
            database.invalidationTracker.refreshVersionsAsync()
            true
        }
    }.subscribeOn(Schedulers.io())

    private fun validate(context: Context, source: String, destination: String) {
        require(source != destination)
        require(ProfileManager.getProfile(context, source) != null)
        require(ProfileManager.getProfile(context, destination) != null)
    }

    private fun copyRows(
        db: SupportSQLiteDatabase,
        table: String,
        source: String,
        destination: String,
        identity: List<String>
    ): Map<Long, Long> = rows(db, table, "profile_id = ?", arrayOf(source)).associate { row ->
        val oldId = row.getAsLong("uid")
        val match = "profile_id = ? AND " + identity.joinToString(" AND ") { "$it IS ?" }
        val args = arrayOf<Any?>(destination, *identity.map { row[it] }.toTypedArray())
        val existing = rows(db, table, match, args).firstOrNull()
        val newId = existing?.getAsLong("uid") ?: run {
            row.remove("uid")
            row.put("profile_id", destination)
            // Append imported rows after the destination's existing order.
            listOf("display_index", "sort_order").filter(row::containsKey).forEach { column ->
                db.query("SELECT COALESCE(MAX($column), -1) + 1 FROM $table WHERE profile_id = ?", arrayOf(destination)).use {
                    it.moveToFirst()
                    row.put(column, it.getLong(0))
                }
            }
            db.insert(table, SQLiteDatabase.CONFLICT_ABORT, row)
        }
        oldId to newId
    }

    private fun copyLinks(
        db: SupportSQLiteDatabase,
        table: String,
        parent: String,
        mapping: Map<Long, Long>,
        child: String? = null,
        children: Map<Long, Long> = emptyMap()
    ) {
        mapping.forEach { (old, new) ->
            rows(db, table, "$parent = ?", arrayOf(old)).forEach { row ->
                row.put(parent, new)
                if (child != null) row.put(child, children.getValue(row.getAsLong(child)))
                db.insert(table, SQLiteDatabase.CONFLICT_IGNORE, row)
            }
        }
    }

    private fun copyPlaylistItems(db: SupportSQLiteDatabase, playlists: Map<Long, Long>) {
        playlists.forEach { (old, new) ->
            val existing = rows(db, "playlist_stream_join", "playlist_id = ?", arrayOf(new))
            val seen = existing.map { it.getAsLong("stream_id") }.toMutableSet()
            var index = (existing.maxOfOrNull { it.getAsLong("join_index") } ?: -1) + 1
            rows(db, "playlist_stream_join", "playlist_id = ? ORDER BY join_index", arrayOf(old)).forEach { row ->
                if (seen.add(row.getAsLong("stream_id"))) {
                    row.put("playlist_id", new)
                    row.put("join_index", index++)
                    db.insert("playlist_stream_join", SQLiteDatabase.CONFLICT_ABORT, row)
                }
            }
        }
    }

    private fun copyLearningSources(db: SupportSQLiteDatabase, playlists: Map<Long, Long>) {
        playlists.forEach { (old, new) ->
            rows(db, "learning_content_sources", "local_playlist_id = ?", arrayOf(old)).forEach { row ->
                val oldSource = row.getAsString("source_id")
                val newSource = "local-playlist:$new"
                row.put("source_id", newSource)
                row.put("local_playlist_id", new)
                db.insert("learning_content_sources", SQLiteDatabase.CONFLICT_IGNORE, row)
                rows(db, "learning_content_streams", "source_id = ?", arrayOf(oldSource)).forEach { membership ->
                    membership.put("source_id", newSource)
                    db.insert("learning_content_streams", SQLiteDatabase.CONFLICT_IGNORE, membership)
                }
            }
        }
    }

    private fun copyHistory(db: SupportSQLiteDatabase, source: String, destination: String, recorder: HistorySyncRecorder) {
        // Sync materializes watch events into one aggregate per video. Keep existing
        // destination histories intact so repeating a merge after sync cannot count them twice.
        val existingVideos = rows(db, "stream_history", "profile_id = ?", arrayOf(destination))
            .map { it.getAsLong("stream_id") }.toSet()
        rows(db, "stream_history", "profile_id = ?", arrayOf(source)).forEach { row ->
            row.put("profile_id", destination)
            if (row.getAsLong("stream_id") !in existingVideos) {
                recorder.recordWatchEventForProfile(
                    destination,
                    row.getAsLong("stream_id"),
                    row.getAsLong("access_date"),
                    row.getAsLong("repeat_count")
                )
                db.insert("stream_history", SQLiteDatabase.CONFLICT_ABORT, row)
            }
        }
        rows(db, "stream_state", "profile_id = ?", arrayOf(source)).forEach { row ->
            row.put("profile_id", destination)
            if (rows(db, "stream_state", "profile_id = ? AND stream_id = ?", arrayOf(destination, row["stream_id"])).isEmpty()) {
                recorder.recordProgressForProfile(destination, row.getAsLong("stream_id"), row.getAsLong("progress_time"), System.currentTimeMillis())
                db.insert("stream_state", SQLiteDatabase.CONFLICT_ABORT, row)
            }
        }
        rows(db, "search_history", "profile_id = ?", arrayOf(source)).forEach { row ->
            val existing = rows(
                db,
                "search_history",
                "profile_id = ? AND service_id = ? AND search IS ? AND creation_date IS ?",
                arrayOf(destination, row["service_id"], row["search"], row["creation_date"])
            )
            if (existing.isEmpty()) {
                row.remove("id")
                row.put("profile_id", destination)
                recorder.recordSearchForProfile(
                    destination,
                    row.getAsInteger("service_id"),
                    row.getAsString("search").orEmpty(),
                    row.getAsLong("creation_date") ?: 0L
                )
                db.insert("search_history", SQLiteDatabase.CONFLICT_ABORT, row)
            }
        }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String, where: String, args: Array<out Any?>): List<ContentValues> = db.query("SELECT * FROM $table WHERE $where", args).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ContentValues().apply {
                        cursor.columnNames.forEachIndexed { index, name ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> putNull(name)
                                Cursor.FIELD_TYPE_INTEGER -> put(name, cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> put(name, cursor.getDouble(index))
                                Cursor.FIELD_TYPE_BLOB -> put(name, cursor.getBlob(index))
                                else -> put(name, cursor.getString(index))
                            }
                        }
                    }
                )
            }
        }
    }
}
