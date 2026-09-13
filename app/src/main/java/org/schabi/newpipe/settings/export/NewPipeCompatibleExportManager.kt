package org.schabi.newpipe.settings.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

/** Creates a data-only backup that current NewPipe releases can restore directly. */
class NewPipeCompatibleExportManager internal constructor(
    private val sourceDatabase: Path,
    private val temporaryDirectory: Path
) {
    constructor(context: Context) : this(
        context.getDatabasePath(AppDatabase.DATABASE_NAME).toPath(),
        context.cacheDir.toPath()
    )

    data class ExportResult(
        val subscriptions: Int,
        val historyItems: Int,
        val progressItems: Int,
        val skippedItems: Int,
        val localPlaylists: Int,
        val remotePlaylists: Int,
        val playlistItems: Int,
        val channelGroups: Int,
        val groupMemberships: Int,
        val searchItems: Int
    )

    /**
     * Writes an archive containing only a NewPipe schema-version-9 database.
     *
     * WizeStream settings and private tables are deliberately excluded so restoring this archive
     * cannot expose NewPipe to WizeStream's newer Room schema.
     */
    @Throws(Exception::class)
    fun export(file: StoredFileHelper): ExportResult {
        Files.createDirectories(temporaryDirectory)
        val portableDatabase = Files.createTempFile(
            temporaryDirectory,
            "newpipe-compatible-",
            ".db"
        )
        return try {
            val result = createDatabase(portableDatabase)
            try {
                ZipOutputStream(
                    SharpOutputStream(file.openAndTruncateStream()).buffered()
                ).use { output ->
                    ZipHelper.addFileToZip(
                        output,
                        BackupFileLocator.FILE_NAME_DB,
                        portableDatabase
                    )
                }
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
            result
        } finally {
            portableDatabase.deleteIfExists()
        }
    }

    internal fun createDatabase(destinationPath: Path): ExportResult {
        check(Files.isRegularFile(sourceDatabase)) {
            "The WizeStream database is not available"
        }
        destinationPath.deleteIfExists()

        val destination = SQLiteDatabase.openDatabase(
            destinationPath.toString(),
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY
        )
        var sourceAttached = false
        try {
            destination.setForeignKeyConstraintsEnabled(false)
            destination.execSQL(
                "ATTACH DATABASE ? AS wizestream_source",
                arrayOf(sourceDatabase.toString())
            )
            sourceAttached = true
            destination.beginTransaction()
            try {
                NEWPIPE_SCHEMA.forEach(destination::execSQL)
                copyPortableData(destination)
                destination.version = NEWPIPE_DATABASE_VERSION
                destination.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table " +
                        "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                destination.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                        "VALUES(42, ?)",
                    arrayOf(NEWPIPE_IDENTITY_HASH)
                )
                destination.setTransactionSuccessful()
            } finally {
                destination.endTransaction()
            }

            validateDatabase(destination)
            val subscriptions = destination.rowCount("subscriptions")
            val historyItems = destination.rowCount("stream_history")
            val progressItems = destination.rowCount("stream_state")
            return ExportResult(
                subscriptions = subscriptions,
                historyItems = historyItems,
                progressItems = progressItems,
                skippedItems =
                    destination.rowCount("wizestream_source.subscriptions") - subscriptions +
                        destination.rowCount("wizestream_source.stream_history") - historyItems +
                        destination.rowCount("wizestream_source.stream_state") - progressItems +
                        destination.skippedRows("remote_playlists") +
                        destination.skippedRows("playlist_stream_join") +
                        destination.skippedRows("feed_group_subscription_join") +
                        destination.skippedRows("search_history"),
                localPlaylists = destination.rowCount("playlists"),
                remotePlaylists = destination.rowCount("remote_playlists"),
                playlistItems = destination.rowCount("playlist_stream_join"),
                channelGroups = destination.rowCount("feed_group"),
                groupMemberships = destination.rowCount("feed_group_subscription_join"),
                searchItems = destination.rowCount("search_history")
            )
        } finally {
            if (sourceAttached) {
                destination.execSQL("DETACH DATABASE wizestream_source")
            }
            destination.close()
        }
    }

    private fun copyPortableData(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO subscriptions " +
                "(uid, service_id, url, name, avatar_url, subscriber_count, description, " +
                "notification_mode) " +
                "SELECT uid, service_id, url, name, avatar_url, subscriber_count, description, " +
                "CASE WHEN notification_mode = 0 THEN 0 ELSE 1 END " +
                "FROM wizestream_source.subscriptions WHERE service_id BETWEEN " +
                "$NEWPIPE_FIRST_SERVICE_ID AND $NEWPIPE_LAST_SERVICE_ID"
        )
        database.execSQL(
            "INSERT INTO streams " +
                "(uid, service_id, url, title, stream_type, duration, uploader, uploader_url, " +
                "thumbnail_url, view_count, textual_upload_date, upload_date, " +
                "is_upload_date_approximation) " +
                "SELECT s.uid, s.service_id, s.url, s.title, s.stream_type, s.duration, " +
                "s.uploader, s.uploader_url, s.thumbnail_url, s.view_count, " +
                "s.textual_upload_date, s.upload_date, s.is_upload_date_approximation " +
                "FROM wizestream_source.streams s " +
                "WHERE s.source_type = 'REMOTE' AND s.service_id BETWEEN " +
                "$NEWPIPE_FIRST_SERVICE_ID AND $NEWPIPE_LAST_SERVICE_ID AND " +
                "(EXISTS (SELECT 1 FROM wizestream_source.stream_history h " +
                "WHERE h.stream_id = s.uid) OR " +
                "EXISTS (SELECT 1 FROM wizestream_source.stream_state st " +
                "WHERE st.stream_id = s.uid) OR " +
                "EXISTS (SELECT 1 FROM wizestream_source.playlist_stream_join j " +
                "INNER JOIN wizestream_source.playlists p ON p.uid = j.playlist_id " +
                "WHERE j.stream_id = s.uid) OR " +
                "EXISTS (SELECT 1 FROM wizestream_source.playlists p " +
                "WHERE p.thumbnail_stream_id = s.uid))"
        )
        database.execSQL(
            "INSERT INTO stream_history (stream_id, access_date, repeat_count) " +
                "SELECT h.stream_id, h.access_date, h.repeat_count " +
                "FROM wizestream_source.stream_history h " +
                "INNER JOIN streams s ON s.uid = h.stream_id"
        )
        database.execSQL(
            "INSERT INTO stream_state (stream_id, progress_time) " +
                "SELECT st.stream_id, st.progress_time " +
                "FROM wizestream_source.stream_state st " +
                "INNER JOIN streams s ON s.uid = st.stream_id"
        )
        copyPlaylists(database)
        copyGroupsAndSearches(database)
    }

    private fun copyPlaylists(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO playlists " +
                "(uid, name, is_thumbnail_permanent, thumbnail_stream_id, display_index) " +
                "SELECT p.uid, p.name, " +
                "CASE WHEN EXISTS (SELECT 1 FROM streams s WHERE s.uid = p.thumbnail_stream_id) " +
                "THEN p.is_thumbnail_permanent ELSE 0 END, " +
                "COALESCE((SELECT s.uid FROM streams s WHERE s.uid = p.thumbnail_stream_id), " +
                "(SELECT j.stream_id FROM wizestream_source.playlist_stream_join j " +
                "INNER JOIN streams s ON s.uid = j.stream_id WHERE j.playlist_id = p.uid " +
                "ORDER BY j.join_index LIMIT 1), -1), p.display_index " +
                "FROM wizestream_source.playlists p"
        )
        database.execSQL(
            "INSERT INTO playlist_stream_join (playlist_id, stream_id, join_index) " +
                "SELECT j.playlist_id, j.stream_id, j.join_index " +
                "FROM wizestream_source.playlist_stream_join j " +
                "INNER JOIN playlists p ON p.uid = j.playlist_id " +
                "INNER JOIN streams s ON s.uid = j.stream_id"
        )
        database.execSQL(
            "INSERT INTO remote_playlists " +
                "(uid, service_id, name, url, thumbnail_url, uploader, display_index, stream_count) " +
                "SELECT uid, service_id, name, url, thumbnail_url, uploader, " +
                "display_index, stream_count FROM wizestream_source.remote_playlists " +
                "WHERE service_id BETWEEN $NEWPIPE_FIRST_SERVICE_ID AND $NEWPIPE_LAST_SERVICE_ID"
        )
    }

    private fun copyGroupsAndSearches(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO feed_group (uid, name, icon_id, sort_order) " +
                "SELECT uid, name, CASE WHEN icon_id BETWEEN 0 AND $NEWPIPE_LAST_GROUP_ICON " +
                "THEN icon_id ELSE 0 END, sort_order FROM wizestream_source.feed_group"
        )
        database.execSQL(
            "INSERT INTO feed_group_subscription_join (group_id, subscription_id) " +
                "SELECT j.group_id, j.subscription_id " +
                "FROM wizestream_source.feed_group_subscription_join j " +
                "INNER JOIN feed_group g ON g.uid = j.group_id " +
                "INNER JOIN subscriptions s ON s.uid = j.subscription_id"
        )
        database.execSQL(
            "INSERT INTO search_history (id, creation_date, service_id, search) " +
                "SELECT id, creation_date, service_id, search FROM wizestream_source.search_history " +
                "WHERE service_id BETWEEN $NEWPIPE_FIRST_SERVICE_ID AND $NEWPIPE_LAST_SERVICE_ID"
        )
    }

    private fun validateDatabase(database: SQLiteDatabase) {
        database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                throw IOException("The portable database failed SQLite integrity validation")
            }
        }
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            if (cursor.moveToFirst()) {
                throw IOException("The portable database contains invalid references")
            }
        }
        val tables = mutableSetOf<String>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables += cursor.getString(0)
            }
        }
        if (!tables.containsAll(REQUIRED_NEWPIPE_TABLES)) {
            throw IOException("The portable database is missing required NewPipe tables")
        }
    }

    private fun SQLiteDatabase.skippedRows(table: String): Int = rowCount("wizestream_source.$table") - rowCount(table)

    private fun SQLiteDatabase.rowCount(table: String): Int = rawQuery(
        "SELECT COUNT(*) FROM $table",
        null
    ).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private companion object {
        const val NEWPIPE_DATABASE_VERSION = 9
        const val NEWPIPE_IDENTITY_HASH = "7591e8039faa74d8c0517dc867af9d3e"
        const val NEWPIPE_FIRST_SERVICE_ID = 0
        const val NEWPIPE_LAST_SERVICE_ID = 4
        const val NEWPIPE_LAST_GROUP_ICON = 38

        val REQUIRED_NEWPIPE_TABLES = setOf(
            "subscriptions",
            "search_history",
            "streams",
            "stream_history",
            "stream_state",
            "playlists",
            "playlist_stream_join",
            "remote_playlists",
            "feed",
            "feed_group",
            "feed_group_subscription_join",
            "feed_last_updated",
            "room_master_table"
        )

        val NEWPIPE_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS subscriptions (" +
                "uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "service_id INTEGER NOT NULL, url TEXT, name TEXT, avatar_url TEXT, " +
                "subscriber_count INTEGER, description TEXT, notification_mode INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_subscriptions_service_id_url " +
                "ON subscriptions (service_id, url)",
            "CREATE TABLE IF NOT EXISTS search_history (" +
                "creation_date INTEGER, service_id INTEGER NOT NULL, search TEXT, " +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS index_search_history_search " +
                "ON search_history (search)",
            "CREATE TABLE IF NOT EXISTS streams (" +
                "uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, " +
                "url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, " +
                "duration INTEGER NOT NULL, uploader TEXT NOT NULL, uploader_url TEXT, " +
                "thumbnail_url TEXT, view_count INTEGER, textual_upload_date TEXT, " +
                "upload_date INTEGER, is_upload_date_approximation INTEGER)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_streams_service_id_url " +
                "ON streams (service_id, url)",
            "CREATE TABLE IF NOT EXISTS stream_history (" +
                "stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, " +
                "repeat_count INTEGER NOT NULL, PRIMARY KEY(stream_id, access_date), " +
                "FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS index_stream_history_stream_id " +
                "ON stream_history (stream_id)",
            "CREATE TABLE IF NOT EXISTS stream_state (" +
                "stream_id INTEGER NOT NULL, progress_time INTEGER NOT NULL, " +
                "PRIMARY KEY(stream_id), FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS playlists (" +
                "uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, " +
                "is_thumbnail_permanent INTEGER NOT NULL, thumbnail_stream_id INTEGER NOT NULL, " +
                "display_index INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS playlist_stream_join (" +
                "playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, " +
                "join_index INTEGER NOT NULL, PRIMARY KEY(playlist_id, join_index), " +
                "FOREIGN KEY(playlist_id) REFERENCES playlists(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_playlist_stream_join_playlist_id_join_index " +
                "ON playlist_stream_join (playlist_id, join_index)",
            "CREATE INDEX IF NOT EXISTS index_playlist_stream_join_stream_id " +
                "ON playlist_stream_join (stream_id)",
            "CREATE TABLE IF NOT EXISTS remote_playlists (" +
                "uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, " +
                "name TEXT, url TEXT, thumbnail_url TEXT, uploader TEXT, " +
                "display_index INTEGER NOT NULL, stream_count INTEGER)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_remote_playlists_service_id_url " +
                "ON remote_playlists (service_id, url)",
            "CREATE TABLE IF NOT EXISTS feed (" +
                "stream_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, " +
                "PRIMARY KEY(stream_id, subscription_id), " +
                "FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
            "CREATE INDEX IF NOT EXISTS index_feed_subscription_id ON feed (subscription_id)",
            "CREATE TABLE IF NOT EXISTS feed_group (" +
                "uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "icon_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS index_feed_group_sort_order ON feed_group (sort_order)",
            "CREATE TABLE IF NOT EXISTS feed_group_subscription_join (" +
                "group_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, " +
                "PRIMARY KEY(group_id, subscription_id), " +
                "FOREIGN KEY(group_id) REFERENCES feed_group(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
            "CREATE INDEX IF NOT EXISTS " +
                "index_feed_group_subscription_join_subscription_id " +
                "ON feed_group_subscription_join (subscription_id)",
            "CREATE TABLE IF NOT EXISTS feed_last_updated (" +
                "subscription_id INTEGER NOT NULL, last_updated INTEGER, " +
                "PRIMARY KEY(subscription_id), " +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
    }
}
