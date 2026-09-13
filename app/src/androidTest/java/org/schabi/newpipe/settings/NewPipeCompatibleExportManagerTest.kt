package org.schabi.newpipe.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.file.Path
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.settings.export.NewPipeCompatibleExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper

@RunWith(AndroidJUnit4::class)
class NewPipeCompatibleExportManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private lateinit var sourcePath: Path
    private lateinit var destinationPath: Path
    private lateinit var archivePath: Path

    @Before
    fun setUp() {
        sourcePath = context.cacheDir.resolve("portable-export-source.db").toPath()
        destinationPath = context.getDatabasePath(PORTABLE_DATABASE_NAME).toPath()
        archivePath = context.cacheDir.resolve("portable-export.zip").toPath()
        sourcePath.toFile().delete()
        context.deleteDatabase(PORTABLE_DATABASE_NAME)
        archivePath.toFile().delete()
        createSourceDatabase(sourcePath)
    }

    @After
    fun tearDown() {
        sourcePath.toFile().delete()
        context.deleteDatabase(PORTABLE_DATABASE_NAME)
        archivePath.toFile().delete()
    }

    @Test
    fun createsNewPipeNineDatabaseWithOnlyPortablePlaybackData() {
        val result = NewPipeCompatibleExportManager(
            sourcePath,
            context.cacheDir.toPath()
        ).createDatabase(destinationPath)

        assertEquals(1, result.subscriptions)
        assertEquals(1, result.historyItems)
        assertEquals(1, result.progressItems)
        assertEquals(3, result.skippedItems)

        migrationTestHelper.runMigrationsAndValidate(
            PORTABLE_DATABASE_NAME,
            9,
            true
        ).close()

        SQLiteDatabase.openDatabase(
            destinationPath.toString(),
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            assertEquals(9, database.version)
            assertEquals(
                "7591e8039faa74d8c0517dc867af9d3e",
                database.singleString(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42"
                )
            )
            assertEquals(EXPECTED_TABLES, database.userTables())
            assertEquals(EXPECTED_SUBSCRIPTION_COLUMNS, database.columns("subscriptions"))
            assertEquals(EXPECTED_STREAM_COLUMNS, database.columns("streams"))
            assertEquals(
                "https://example.com/remote",
                database.singleString("SELECT url FROM streams")
            )
            assertEquals(
                "https://example.com/channel",
                database.singleString("SELECT url FROM subscriptions")
            )
            assertEquals(1, database.singleInt("SELECT notification_mode FROM subscriptions"))
            assertEquals("ok", database.singleString("PRAGMA quick_check"))
            database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun preservesPlaylistsGroupsAndSearchesWithOnlyCompatibleReferences() {
        SQLiteDatabase.openDatabase(sourcePath.toString(), null, SQLiteDatabase.OPEN_READWRITE).use { source ->
            source.execSQL(
                "INSERT INTO streams VALUES " +
                    "(40, 0, 'https://example.com/thumbnail', 'Thumbnail', 'VIDEO_STREAM', 60, " +
                    "'Uploader', NULL, NULL, NULL, NULL, NULL, 0, 'REMOTE')"
            )
            source.execSQL(
                "INSERT INTO playlists VALUES " +
                    "(1, 'Mixed', 1, 20, 4), (2, 'Empty', 1, 999, 2), " +
                    "(3, 'Device only', 1, 20, 1), (4, 'Custom thumbnail', 1, 40, 3)"
            )
            source.execSQL(
                "INSERT INTO playlist_stream_join VALUES " +
                    "(1, 30, 4), (1, 20, 7), (1, 10, 9), (1, 10, 11), (3, 20, 0)"
            )
            source.execSQL(
                "INSERT INTO remote_playlists VALUES " +
                    "(6, 0, 'Remote playlist', 'https://example.com/playlist', 'https://example.com/image', 'Owner', 17, 2), " +
                    "(7, 5, 'Unsupported playlist', 'https://example.com/unsupported', NULL, NULL, 18, 1)"
            )
            source.execSQL("INSERT INTO feed_group VALUES (11, 'News', 8, 3), (12, 'Empty group', 999, 2)")
            source.execSQL("INSERT INTO feed_group_subscription_join VALUES (11, 1), (11, 2)")
            source.execSQL("INSERT INTO search_history VALUES (1000, 0, 'space', 1), (2000, 0, 'space', 2), (3000, 4, 'music', 3), (4000, 5, 'unsupported', 4)")
        }
        val result = NewPipeCompatibleExportManager(sourcePath, context.cacheDir.toPath()).createDatabase(destinationPath)
        assertEquals(4, result.localPlaylists)
        assertEquals(1, result.remotePlaylists)
        assertEquals(3, result.playlistItems)
        assertEquals(2, result.channelGroups)
        assertEquals(1, result.groupMemberships)
        assertEquals(3, result.searchItems)
        assertEquals(8, result.skippedItems)
        migrationTestHelper.runMigrationsAndValidate(PORTABLE_DATABASE_NAME, 9, true).close()
        SQLiteDatabase.openDatabase(destinationPath.toString(), null, SQLiteDatabase.OPEN_READONLY).use { database ->
            assertEquals("30,10,10", database.singleString("SELECT group_concat(stream_id) FROM (SELECT stream_id FROM playlist_stream_join WHERE playlist_id=1 ORDER BY join_index)"))
            assertEquals(30, database.singleInt("SELECT thumbnail_stream_id FROM playlists WHERE uid=1"))
            assertEquals(0, database.singleInt("SELECT is_thumbnail_permanent FROM playlists WHERE uid=1"))
            assertEquals(-1, database.singleInt("SELECT thumbnail_stream_id FROM playlists WHERE uid=2"))
            assertEquals(-1, database.singleInt("SELECT thumbnail_stream_id FROM playlists WHERE uid=3"))
            assertEquals(40, database.singleInt("SELECT thumbnail_stream_id FROM playlists WHERE uid=4"))
            assertEquals(1, database.singleInt("SELECT is_thumbnail_permanent FROM playlists WHERE uid=4"))
            assertEquals(3, database.singleInt("SELECT COUNT(*) FROM streams"))
            assertEquals(17, database.singleInt("SELECT display_index FROM remote_playlists WHERE uid=6"))
            assertEquals("Owner", database.singleString("SELECT uploader FROM remote_playlists WHERE uid=6"))
            assertEquals(8, database.singleInt("SELECT icon_id FROM feed_group WHERE uid=11"))
            assertEquals(0, database.singleInt("SELECT icon_id FROM feed_group WHERE uid=12"))
            assertEquals(1, database.singleInt("SELECT subscription_id FROM feed_group_subscription_join WHERE group_id=11"))
            assertEquals("1000,2000", database.singleString("SELECT group_concat(creation_date) FROM (SELECT creation_date FROM search_history WHERE search='space' ORDER BY creation_date)"))
            assertEquals("ok", database.singleString("PRAGMA quick_check"))
            database.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
        SQLiteDatabase.openDatabase(sourcePath.toString(), null, SQLiteDatabase.OPEN_READONLY).use { source ->
            assertEquals(5, source.singleInt("SELECT COUNT(*) FROM playlist_stream_join"))
            assertEquals(2, source.singleInt("SELECT COUNT(*) FROM feed_group_subscription_join"))
            assertEquals(4, source.singleInt("SELECT COUNT(*) FROM search_history"))
        }
    }

    @Test
    fun archiveContainsOnlyThePortableNewPipeDatabase() {
        archivePath.toFile().createNewFile()
        val file = StoredFileHelper(
            context,
            null,
            Uri.fromFile(archivePath.toFile()),
            "portable-export-test"
        )

        NewPipeCompatibleExportManager(sourcePath, context.cacheDir.toPath()).export(file)

        ZipFile(archivePath.toFile()).use { archive ->
            assertEquals(listOf("newpipe.db"), archive.entries().toList().map { it.name })
        }
    }

    private fun createSourceDatabase(path: Path) {
        SQLiteDatabase.openOrCreateDatabase(path.toFile(), null).use { database ->
            database.execSQL(
                "CREATE TABLE subscriptions (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, url TEXT, " +
                    "name TEXT, avatar_url TEXT, subscriber_count INTEGER, description TEXT, " +
                    "notification_mode INTEGER NOT NULL, youtube_mode_mask INTEGER NOT NULL, " +
                    "notification_keywords TEXT NOT NULL)"
            )
            database.execSQL(
                "INSERT INTO subscriptions VALUES " +
                    "(1, 0, 'https://example.com/channel', 'Channel', NULL, " +
                    "100, NULL, 2, 1, ''), " +
                    "(2, 5, 'https://example.com/wizestream-only', 'WizeStream only', NULL, " +
                    "100, NULL, 1, 1, '')"
            )
            database.execSQL(
                "CREATE TABLE streams (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, url TEXT NOT NULL, " +
                    "title TEXT NOT NULL, stream_type TEXT NOT NULL, duration INTEGER NOT NULL, " +
                    "uploader TEXT NOT NULL, uploader_url TEXT, thumbnail_url TEXT, " +
                    "view_count INTEGER, textual_upload_date TEXT, upload_date INTEGER, " +
                    "is_upload_date_approximation INTEGER, source_type TEXT NOT NULL)"
            )
            database.execSQL(
                "INSERT INTO streams VALUES " +
                    "(10, 0, 'https://example.com/remote', 'Remote', 'VIDEO_STREAM', 120, " +
                    "'Uploader', NULL, NULL, 10, NULL, NULL, 0, 'REMOTE'), " +
                    "(20, -1, 'local://20', 'Local', 'VIDEO_STREAM', 60, 'Device', NULL, NULL, " +
                    "NULL, NULL, NULL, 0, 'LOCAL'), " +
                    "(30, 0, 'https://example.com/unwatched', 'Unwatched', 'VIDEO_STREAM', 60, " +
                    "'Uploader', NULL, NULL, NULL, NULL, NULL, 0, 'REMOTE')"
            )
            database.execSQL(
                "CREATE TABLE stream_history (" +
                    "stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, " +
                    "repeat_count INTEGER NOT NULL)"
            )
            database.execSQL(
                "INSERT INTO stream_history VALUES (10, 1000, 1), (20, 2000, 1)"
            )
            database.execSQL(
                "CREATE TABLE stream_state (" +
                    "stream_id INTEGER PRIMARY KEY, progress_time INTEGER NOT NULL)"
            )
            database.execSQL("INSERT INTO stream_state VALUES (10, 30000), (20, 15000)")
            database.execSQL("CREATE TABLE playlists (uid INTEGER PRIMARY KEY, name TEXT, is_thumbnail_permanent INTEGER NOT NULL, thumbnail_stream_id INTEGER NOT NULL, display_index INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE playlist_stream_join (playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, join_index INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE remote_playlists (uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, name TEXT, url TEXT, thumbnail_url TEXT, uploader TEXT, display_index INTEGER NOT NULL, stream_count INTEGER)")
            database.execSQL("CREATE TABLE feed_group (uid INTEGER PRIMARY KEY, name TEXT NOT NULL, icon_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE feed_group_subscription_join (group_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE search_history (creation_date INTEGER, service_id INTEGER NOT NULL, search TEXT, id INTEGER PRIMARY KEY)")
        }
    }

    private fun SQLiteDatabase.singleString(query: String): String = rawQuery(
        query,
        null
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun SQLiteDatabase.singleInt(query: String): Int = rawQuery(
        query,
        null
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun SQLiteDatabase.userTables(): Set<String> {
        val result = mutableSetOf<String>()
        rawQuery(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.getString(0)
            }
        }
        return result
    }

    private fun SQLiteDatabase.columns(table: String): Set<String> {
        val result = mutableSetOf<String>()
        rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }
        return result
    }

    private companion object {
        const val PORTABLE_DATABASE_NAME = "portable-export-destination.db"
        val EXPECTED_TABLES = setOf(
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
        val EXPECTED_SUBSCRIPTION_COLUMNS = setOf(
            "uid",
            "service_id",
            "url",
            "name",
            "avatar_url",
            "subscriber_count",
            "description",
            "notification_mode"
        )
        val EXPECTED_STREAM_COLUMNS = setOf(
            "uid",
            "service_id",
            "url",
            "title",
            "stream_type",
            "duration",
            "uploader",
            "uploader_url",
            "thumbnail_url",
            "view_count",
            "textual_upload_date",
            "upload_date",
            "is_upload_date_approximation"
        )
    }
}
