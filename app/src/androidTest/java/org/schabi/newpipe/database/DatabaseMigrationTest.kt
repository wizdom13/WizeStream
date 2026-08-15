package org.schabi.newpipe.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    companion object {
        private const val DEFAULT_SERVICE_ID = 0
        private const val DEFAULT_URL = "https://www.youtube.com/watch?v=cDphUib5iG4"
        private const val DEFAULT_TITLE = "Test Title"
        private const val DEFAULT_NAME = "Test Name"
        private val DEFAULT_TYPE = StreamType.VIDEO_STREAM
        private const val DEFAULT_DURATION = 480L
        private const val DEFAULT_UPLOADER_NAME = "Uploader Test"
        private const val DEFAULT_UPLOADER_AVATAR = "https://example.com/avatar.jpg"
        private const val DEFAULT_THUMBNAIL = "https://example.com/example.jpg"

        private const val DEFAULT_SECOND_SERVICE_ID = 1
        private const val DEFAULT_SECOND_URL = "https://www.youtube.com/watch?v=ncQU6iBn5Fc"

        private const val DEFAULT_THIRD_SERVICE_ID = 2
        private const val DEFAULT_THIRD_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrateDatabaseFrom2to3() {
        val databaseInV2 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_2)

        databaseInV2.run {
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                    put("thumbnail_url", DEFAULT_THUMBNAIL)
                }
            )
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SECOND_SERVICE_ID)
                    put("url", DEFAULT_SECOND_URL)
                }
            )
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_3,
            true,
            Migrations.MIGRATION_2_3
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_4,
            true,
            Migrations.MIGRATION_3_4
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_5,
            true,
            Migrations.MIGRATION_4_5
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_6,
            true,
            Migrations.MIGRATION_5_6
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_7,
            true,
            Migrations.MIGRATION_6_7
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_8,
            true,
            Migrations.MIGRATION_7_8
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9,
            true,
            Migrations.MIGRATION_8_9
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )

        val migratedDatabaseV3 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV3.streamDAO().getAll().blockingFirst()

        // Only expect 2, the one with the null url will be ignored
        assertEquals(2, listFromDB.size)

        val streamFromMigratedDatabase = listFromDB[0]
        assertEquals(DEFAULT_SERVICE_ID, streamFromMigratedDatabase.serviceId)
        assertEquals(DEFAULT_URL, streamFromMigratedDatabase.url)
        assertEquals(DEFAULT_TITLE, streamFromMigratedDatabase.title)
        assertEquals(DEFAULT_TYPE, streamFromMigratedDatabase.streamType)
        assertEquals(DEFAULT_DURATION, streamFromMigratedDatabase.duration)
        assertEquals(DEFAULT_UPLOADER_NAME, streamFromMigratedDatabase.uploader)
        assertEquals(DEFAULT_THUMBNAIL, streamFromMigratedDatabase.thumbnailUrl)
        assertNull(streamFromMigratedDatabase.viewCount)
        assertNull(streamFromMigratedDatabase.textualUploadDate)
        assertNull(streamFromMigratedDatabase.uploadDate)
        assertNull(streamFromMigratedDatabase.isUploadDateApproximation)

        val secondStreamFromMigratedDatabase = listFromDB[1]
        assertEquals(DEFAULT_SECOND_SERVICE_ID, secondStreamFromMigratedDatabase.serviceId)
        assertEquals(DEFAULT_SECOND_URL, secondStreamFromMigratedDatabase.url)
        assertEquals("", secondStreamFromMigratedDatabase.title)
        // Should fallback to VIDEO_STREAM
        assertEquals(StreamType.VIDEO_STREAM, secondStreamFromMigratedDatabase.streamType)
        assertEquals(0, secondStreamFromMigratedDatabase.duration)
        assertEquals("", secondStreamFromMigratedDatabase.uploader)
        assertEquals("", secondStreamFromMigratedDatabase.thumbnailUrl)
        assertNull(secondStreamFromMigratedDatabase.viewCount)
        assertNull(secondStreamFromMigratedDatabase.textualUploadDate)
        assertNull(secondStreamFromMigratedDatabase.uploadDate)
        assertNull(secondStreamFromMigratedDatabase.isUploadDateApproximation)
    }

    @Test
    fun migrateDatabaseFrom7to8() {
        val databaseInV7 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_7)

        val defaultSearch1 = " abc "
        val defaultSearch2 = " abc"

        val serviceId = DEFAULT_SERVICE_ID // YouTube
        // Use id different to YouTube because two searches with the same query
        // but different service are considered not equal.
        val otherServiceId = ServiceList.SoundCloud.serviceId

        databaseInV7.run {
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", serviceId)
                    put("search", defaultSearch1)
                }
            )
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", serviceId)
                    put("search", defaultSearch2)
                }
            )
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", otherServiceId)
                    put("search", defaultSearch1)
                }
            )
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", otherServiceId)
                    put("search", defaultSearch2)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_8,
            true,
            Migrations.MIGRATION_7_8
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9,
            true,
            Migrations.MIGRATION_8_9
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )

        val migratedDatabaseV8 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV8.searchHistoryDAO().getAll().blockingFirst()

        assertEquals(2, listFromDB.size)
        assertEquals("abc", listFromDB[0].search)
        assertEquals("abc", listFromDB[1].search)
        assertNotEquals(listFromDB[0].serviceId, listFromDB[1].serviceId)
    }

    @Test
    fun migrateDatabaseFrom8to9() {
        val databaseInV8 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_8)

        val localUid1: Long
        val localUid2: Long
        val remoteUid1: Long
        val remoteUid2: Long
        databaseInV8.run {
            localUid1 = insert(
                "playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("name", DEFAULT_NAME + "1")
                    put("is_thumbnail_permanent", false)
                    put("thumbnail_stream_id", -1)
                }
            )
            localUid2 = insert(
                "playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("name", DEFAULT_NAME + "2")
                    put("is_thumbnail_permanent", false)
                    put("thumbnail_stream_id", -1)
                }
            )
            delete(
                "playlists",
                "uid = ?",
                Array(1) { localUid1 }
            )
            remoteUid1 = insert(
                "remote_playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                }
            )
            remoteUid2 = insert(
                "remote_playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SECOND_SERVICE_ID)
                    put("url", DEFAULT_SECOND_URL)
                }
            )
            delete(
                "remote_playlists",
                "uid = ?",
                Array(1) { remoteUid2 }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9,
            true,
            Migrations.MIGRATION_8_9
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )

        val migratedDatabaseV9 = getMigratedDatabase()
        var localListFromDB = migratedDatabaseV9.playlistDAO().getAll().blockingFirst()
        var remoteListFromDB = migratedDatabaseV9.playlistRemoteDAO().getAll().blockingFirst()

        assertEquals(1, localListFromDB.size)
        assertEquals(localUid2, localListFromDB[0].uid)
        assertEquals(-1, localListFromDB[0].displayIndex)
        assertEquals(1, remoteListFromDB.size)
        assertEquals(remoteUid1, remoteListFromDB[0].uid)
        assertEquals(-1, remoteListFromDB[0].displayIndex)

        val localUid3 = migratedDatabaseV9.playlistDAO().insert(
            PlaylistEntity(
                name = "${DEFAULT_NAME}3",
                isThumbnailPermanent = false,
                thumbnailStreamId = -1,
                displayIndex = -1
            )
        )
        val remoteUid3 = migratedDatabaseV9.playlistRemoteDAO().insert(
            PlaylistRemoteEntity(
                serviceId = DEFAULT_THIRD_SERVICE_ID,
                orderingName = DEFAULT_NAME,
                url = DEFAULT_THIRD_URL,
                thumbnailUrl = DEFAULT_THUMBNAIL,
                uploader = DEFAULT_UPLOADER_NAME,
                displayIndex = -1,
                streamCount = 10
            )
        )

        localListFromDB = migratedDatabaseV9.playlistDAO().getAll().blockingFirst()
        remoteListFromDB = migratedDatabaseV9.playlistRemoteDAO().getAll().blockingFirst()
        assertEquals(2, localListFromDB.size)
        assertEquals(localUid3, localListFromDB[1].uid)
        assertEquals(-1, localListFromDB[1].displayIndex)
        assertEquals(2, remoteListFromDB.size)
        assertEquals(remoteUid3, remoteListFromDB[1].uid)
        assertEquals(-1, remoteListFromDB[1].displayIndex)
    }

    @Test
    fun migrateDatabaseFrom9to10() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom10to11() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom11to12() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom12to13() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom13to14DefaultsSubscriptionsToRegularYoutube() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13
        ).apply {
            insert(
                "subscriptions",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("name", DEFAULT_NAME)
                    put("notification_mode", 0)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_14,
            true,
            Migrations.MIGRATION_13_14
        )

        val subscription = getMigratedDatabase()
            .subscriptionDAO()
            .getSubscriptionDirect(DEFAULT_SERVICE_ID, DEFAULT_URL)!!
        assertEquals(1, subscription.youtubeModeMask)
    }

    @Test
    fun migrateDatabaseFrom14to15SeparatesYoutubeFeedModes() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_14
        ).apply {
            insert(
                "subscriptions",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 1)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("name", DEFAULT_NAME)
                    put("notification_mode", 0)
                    put("youtube_mode_mask", 3)
                }
            )
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 1)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                }
            )
            insert(
                "feed",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("stream_id", 1)
                    put("subscription_id", 1)
                }
            )
            insert(
                "feed_last_updated",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("subscription_id", 1)
                    put("last_updated", 1234)
                }
            )
            close()
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_15,
            true,
            Migrations.MIGRATION_14_15
        )
        migrated.query(
            "SELECT youtube_mode_mask FROM feed ORDER BY youtube_mode_mask"
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.moveToNext()
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query(
            "SELECT youtube_mode_mask FROM feed_last_updated ORDER BY youtube_mode_mask"
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.moveToNext()
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrateDatabaseFrom15to16AddsUploaderAvatar() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_15
        ).apply {
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 1)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                }
            )
            close()
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_16,
            true,
            Migrations.MIGRATION_15_16
        )
        migrated.query(
            "SELECT uploader_avatar_url FROM streams WHERE uid = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }

        migrated.execSQL(
            "UPDATE streams SET uploader_avatar_url = ? WHERE uid = 1",
            arrayOf(DEFAULT_UPLOADER_AVATAR)
        )
        migrated.query(
            "SELECT uploader_avatar_url FROM streams WHERE uid = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(DEFAULT_UPLOADER_AVATAR, cursor.getString(0))
        }
    }

    @Test
    fun migrateDatabaseFrom16to17AddsLearningNotes() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_16
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_17,
            true,
            Migrations.MIGRATION_16_17
        )
        migrated.query("PRAGMA table_info(`learning_notes`)").use { cursor ->
            assertEquals(6, cursor.count)
        }
    }

    @Test
    fun migrateDatabaseFrom17to18AddsLearningSessions() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_17
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_18,
            true,
            Migrations.MIGRATION_17_18
        )
        migrated.query("PRAGMA table_info(`learning_sessions`)").use { cursor ->
            assertEquals(7, cursor.count)
        }
    }

    @Test
    fun migrateDatabaseFrom18to19AddsMembershipRestriction() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_18
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_19,
            true,
            Migrations.MIGRATION_18_19
        )
        migrated.query("PRAGMA table_info(streams)").use { cursor ->
            val columnNameIndex = cursor.getColumnIndex("name")
            var foundMembershipColumn = false
            while (cursor.moveToNext()) {
                foundMembershipColumn = foundMembershipColumn ||
                    cursor.getString(columnNameIndex) == "requires_membership"
            }
            assertTrue(foundMembershipColumn)
        }
    }

    @Test
    fun migrateDatabaseFrom19to20AddsLocalMediaSourceColumns() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_19
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_20,
            true,
            Migrations.MIGRATION_19_20
        )
        migrated.query("PRAGMA table_info(streams)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            val columns = mutableMapOf<String, String?>()
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getString(defaultIndex)
            }
            assertEquals("'REMOTE'", columns["source_type"])
            assertTrue(columns.containsKey("mime_type"))
            assertTrue(columns.containsKey("local_media_id"))
            assertTrue(columns.containsKey("local_album"))
            assertTrue(columns.containsKey("local_folder"))
        }
    }

    private fun getMigratedDatabase(): AppDatabase {
        val database: AppDatabase = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                Migrations.MIGRATION_13_14,
                Migrations.MIGRATION_14_15,
                Migrations.MIGRATION_15_16,
                Migrations.MIGRATION_16_17,
                Migrations.MIGRATION_17_18,
                Migrations.MIGRATION_18_19,
                Migrations.MIGRATION_19_20
            )
            .build()
        testHelper.closeWhenFinished(database)
        return database
    }
}
