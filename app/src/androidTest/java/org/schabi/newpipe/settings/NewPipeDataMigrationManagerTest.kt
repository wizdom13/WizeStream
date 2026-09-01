package org.schabi.newpipe.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.settings.export.NewPipeDataMigrationManager
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig

@RunWith(AndroidJUnit4::class)
class NewPipeDataMigrationManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var sourcePath: Path
    private var originalPreferences: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        NewPipeDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        originalPreferences = buildMap {
            preferences.all.forEach { (key, value) ->
                if (value != null) {
                    put(key, value)
                }
            }
        }
        preferences.edit().clear().commit()
        sourcePath = context.cacheDir.resolve("newpipe-migration-source.db").toPath()
        sourcePath.toFile().delete()
        createSourceDatabase(sourcePath)
    }

    @After
    fun tearDown() {
        NewPipeDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        restorePreferences()
        sourcePath.toFile().delete()
    }

    @Test
    fun historyProgressAndPlaylistsAreMergedWithoutReplacingExistingData() {
        val target = NewPipeDatabase.getInstance(context)
        target.playlistDAO().insert(
            PlaylistEntity(
                name = "Lessons",
                isThumbnailPermanent = false,
                thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                displayIndex = 0
            )
        )
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath)
        assertEquals(1, preview.historyItems)
        assertEquals(1, preview.progressItems)
        assertEquals(1, preview.playlists)
        assertEquals(1, preview.playlistItems)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = true,
                importPlaylists = true
            )
        )

        assertEquals(1, result.historyItems)
        assertEquals(1, result.progressItems)
        assertEquals(1, result.playlists)
        assertEquals(1, result.playlistItems)
        assertEquals(1, target.streamDAO().getAll().blockingFirst().size)
        assertEquals(1, target.streamHistoryDAO().getAllDirect().size)
        assertEquals(90_000, target.streamStateDAO().getAllDirect().single().progressMillis)
        val playlists = target.playlistDAO().getAllDirect()
        assertEquals(2, playlists.size)
        assertTrue(playlists.any { it.name == "Lessons (Imported)" })
        val imported = playlists.single { it.name == "Lessons (Imported)" }
        assertEquals(1, target.playlistStreamDAO().getOrderedStreamsDirect(imported.uid).size)
    }

    @Test
    fun unselectedHistoryIsNotImportedWithPlaylists() {
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = true
            )
        )

        val target = NewPipeDatabase.getInstance(context)
        assertEquals(0, result.historyItems)
        assertEquals(0, result.progressItems)
        assertTrue(target.streamHistoryDAO().getAllDirect().isEmpty())
        assertTrue(target.streamStateDAO().getAllDirect().isEmpty())
        assertEquals(1, target.playlistDAO().getAllDirect().size)
    }

    @Test
    fun pipePipePlaylistAndStreamOrderMatchesItsVisibleOrder() {
        sourcePath.toFile().delete()
        createPipePipeSourceDatabase(sourcePath)
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = true
            )
        )

        val target = NewPipeDatabase.getInstance(context)
        assertEquals(2, result.playlists)
        assertEquals(4, result.playlistItems)
        val playlists = target.playlistDAO().getAllDirect().sortedBy { it.displayIndex }
        assertEquals(listOf("Alpha", "Zulu"), playlists.map { it.name })
        val alphaStreams = target.playlistStreamDAO()
            .getOrderedStreamsDirect(playlists.first().uid)
        assertEquals(
            listOf("First lesson", "Second lesson", "Third lesson"),
            alphaStreams.map { it.title }
        )
    }

    @Test
    fun compatibleSettingsAreTypeCheckedAndMergedWithoutClearingOtherPreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString("existing_wizestream_setting", "keep me")
            .putBoolean(context.getString(R.string.show_comments_key), true)
            .commit()
        val sourcePreferences = mapOf<String, Any>(
            context.getString(R.string.current_service_key) to "YouTube",
            context.getString(R.string.show_comments_key) to false,
            context.getString(R.string.playback_speed_key) to 1.25f,
            context.getString(R.string.show_next_video_key) to "wrong type",
            context.getString(R.string.proxy_password_key) to "secret"
        )
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath, sourcePreferences)
        assertEquals(2, preview.compatibleSettings)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = false,
                importSettings = true
            ),
            sourcePreferences
        )

        assertEquals(2, result.compatibleSettings)
        assertFalse(preferences.contains("service"))
        assertFalse(preferences.getBoolean("show_comments", true))
        assertEquals(1.25f, preferences.getFloat("playback_speed_key", 0f))
        assertFalse(preferences.contains("show_next_video"))
        assertFalse(preferences.contains("proxy_password"))
        assertEquals("keep me", preferences.getString("existing_wizestream_setting", null))
    }

    @Test
    fun pipePipeSponsorBlockSettingsAreConvertedWithoutInventingMissingDefaults() {
        sourcePath.toFile().delete()
        createPipePipeSourceDatabase(sourcePath)
        val sourcePreferences = mapOf<String, Any>(
            context.getString(R.string.sponsor_block_enable_key) to true,
            context.getString(R.string.sponsor_block_notifications_key) to false,
            "sponsor_block_category_sponsor" to true,
            "sponsor_block_category_intro" to false,
            "sponsor_block_category_sponsor_mode" to "automatic",
            "sponsor_block_category_intro_mode" to "manual",
            "sponsor_block_category_outro_mode" to "highlight",
            "sponsor_block_category_sponsor_color" to "#123456",
            context.getString(R.string.sponsor_block_user_id_key) to "do not import"
        )
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath, sourcePreferences)
        assertEquals(NewPipeDataMigrationManager.SourceApp.PIPEPIPE, preview.sourceApp)
        assertEquals(8, preview.sponsorBlockSettings)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = false,
                importSettings = false,
                importSponsorBlock = true
            ),
            sourcePreferences
        )

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(8, result.sponsorBlockSettings)
        assertTrue(preferences.getBoolean("sponsor_block_enable", false))
        assertFalse(preferences.getBoolean("sponsor_block_notifications", true))
        assertFalse(preferences.contains("sponsor_block_graced_rewind"))
        assertTrue(preferences.getBoolean("sponsor_block_category_sponsor", false))
        assertFalse(preferences.getBoolean("sponsor_block_category_intro", true))
        assertEquals(
            SponsorBlockBehavior.SKIP.value,
            preferences.getString(SponsorBlockCategoryConfig.SPONSOR.behaviorKey(), null)
        )
        assertEquals(
            SponsorBlockBehavior.MANUAL.value,
            preferences.getString(SponsorBlockCategoryConfig.INTRO.behaviorKey(), null)
        )
        assertEquals(
            SponsorBlockBehavior.DONT_SKIP.value,
            preferences.getString(SponsorBlockCategoryConfig.OUTRO.behaviorKey(), null)
        )
        assertEquals(
            0xFF123456L,
            preferences.getLong(SponsorBlockCategoryConfig.SPONSOR.colorKey(), 0L)
        )
        assertFalse(preferences.contains("sponsor_block_user_id"))
    }

    private fun restorePreferences() {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit())
    }

    private fun createSourceDatabase(path: Path) {
        SQLiteDatabase.openOrCreateDatabase(path.toFile(), null).use { source ->
            source.execSQL(
                "CREATE TABLE streams (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, " +
                    "duration INTEGER NOT NULL, uploader TEXT NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE stream_history (" +
                    "stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, " +
                    "repeat_count INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE stream_state (" +
                    "stream_id INTEGER PRIMARY KEY, progress_time INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE playlists (uid INTEGER PRIMARY KEY, name TEXT)"
            )
            source.execSQL(
                "CREATE TABLE playlist_stream_join (" +
                    "playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, " +
                    "join_index INTEGER NOT NULL)"
            )
            source.execSQL(
                "INSERT INTO streams VALUES " +
                    "(1, 0, 'https://example.com/watch/1', 'Lesson one', " +
                    "'VIDEO_STREAM', 300, 'Teacher')"
            )
            source.execSQL("INSERT INTO stream_history VALUES (1, 1788253200000, 2)")
            source.execSQL("INSERT INTO stream_state VALUES (1, 90000)")
            source.execSQL("INSERT INTO playlists VALUES (10, 'Lessons')")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (10, 1, 0)")
        }
    }

    private fun createPipePipeSourceDatabase(path: Path) {
        SQLiteDatabase.openOrCreateDatabase(path.toFile(), null).use { source ->
            source.execSQL("PRAGMA user_version = 901")
            source.execSQL(
                "CREATE TABLE streams (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, " +
                    "duration INTEGER NOT NULL, uploader TEXT NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE playlists (" +
                    "uid INTEGER PRIMARY KEY, name TEXT, display_index INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE playlist_stream_join (" +
                    "playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, " +
                    "join_index INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE sponsorblock_whitelist (uploader TEXT PRIMARY KEY NOT NULL)"
            )
            source.execSQL(
                "INSERT INTO streams VALUES " +
                    "(1, 0, 'https://example.com/watch/1', 'First lesson', " +
                    "'VIDEO_STREAM', 300, 'Teacher'), " +
                    "(2, 0, 'https://example.com/watch/2', 'Second lesson', " +
                    "'VIDEO_STREAM', 300, 'Teacher'), " +
                    "(3, 0, 'https://example.com/watch/3', 'Third lesson', " +
                    "'VIDEO_STREAM', 300, 'Teacher')"
            )
            source.execSQL("INSERT INTO playlists VALUES (10, 'Zulu', 0), (20, 'Alpha', 1)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (20, 3, 2)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (20, 1, 0)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (20, 2, 1)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (10, 2, 0)")
        }
    }
}
