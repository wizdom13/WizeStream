package org.schabi.newpipe.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.settings.export.NewPipeDataMigrationManager

@RunWith(AndroidJUnit4::class)
class NewPipeDataMigrationManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var sourcePath: Path

    @Before
    fun setUp() {
        NewPipeDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        sourcePath = context.cacheDir.resolve("newpipe-migration-source.db").toPath()
        sourcePath.toFile().delete()
        createSourceDatabase(sourcePath)
    }

    @After
    fun tearDown() {
        NewPipeDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
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
}
