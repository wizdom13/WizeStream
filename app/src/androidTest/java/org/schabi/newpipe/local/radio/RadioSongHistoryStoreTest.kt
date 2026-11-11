package org.schabi.newpipe.local.radio

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.export.HistoryArchiveCodec
import org.schabi.newpipe.settings.export.HistoryArchiveStore
import org.schabi.newpipe.testUtil.TestDatabase

class RadioSongHistoryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val historyKey = context.getString(R.string.enable_watch_history_key)
    private val previousHistory = preferences.getBoolean(historyKey, true)
    private val previousIncognito = IncognitoMode.isEnabled(context)
    private val station = PlayQueueItem.radioStation("Radio", "https://example.org/live", "Country", false, "https://example.org/logo.png")
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        preferences.edit().putBoolean(historyKey, true).putBoolean(IncognitoMode.KEY, false).commit()
    }

    @After
    fun cleanup() {
        preferences.edit().putBoolean(historyKey, previousHistory).putBoolean(IncognitoMode.KEY, previousIncognito).commit()
        database.close()
    }

    @Test
    fun songsPersistInProfileHistoryAndRoundTripThroughArchive() {
        val profile = ProfileManager.getActiveProfileId(context)
        val store = RadioSongHistoryStore(context)
        store.record(station, "First song", profile).blockingAwait()
        store.record(station, "Second song", profile).blockingAwait()
        store.record(station, "First song", profile).blockingAwait()
        val entries = HistoryRecordManager(context).streamStatistics.blockingFirst()
        assertEquals(2, entries.size)
        val first = entries.single { it.streamEntity.title == "First song" }
        assertEquals("Radio", first.streamEntity.uploader)
        assertEquals(2L, first.watchCount)
        assertTrue(first.streamEntity.isRadio)
        assertEquals(station.url, first.toPlayQueueItem().url)
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile("other-profile").isEmpty())
        val archive = HistoryArchiveStore(database, profile).export()
        val decoded = HistoryArchiveCodec.decode(HistoryArchiveCodec.encode(archive).byteInputStream())
        assertEquals(archive, decoded)
        assertEquals(2, HistoryArchiveStore(database, "restored-profile").merge(decoded))
        val restored = HistoryRecordManager(context, "restored-profile").streamStatistics.blockingFirst()
        assertTrue(restored.all { it.toPlayQueueItem().isRadio })
        HistoryRecordManager(context).deleteStreamHistoryAndState(first.streamId).blockingAwait()
        assertEquals(1, HistoryRecordManager(context).streamStatistics.blockingFirst().size)
        assertEquals(2, HistoryRecordManager(context, "restored-profile").streamStatistics.blockingFirst().size)
    }

    @Test
    fun queuedWritesRecheckIncognitoHistoryAndActiveProfile() {
        val profile = ProfileManager.getActiveProfileId(context)
        val store = RadioSongHistoryStore(context)
        val queued = store.record(station, "Private", profile)
        IncognitoMode.setEnabled(context, true)
        assertFalse(store.enabled())
        queued.blockingAwait()
        IncognitoMode.setEnabled(context, false)
        preferences.edit().putBoolean(historyKey, false).commit()
        store.record(station, "Disabled", profile).blockingAwait()
        preferences.edit().putBoolean(historyKey, true).commit()
        store.record(station, "Wrong profile", "not-active").blockingAwait()
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(profile).isEmpty())
        assertTrue(database.streamDAO().getAll().blockingFirst().isEmpty())
    }
}
