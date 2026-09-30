package org.schabi.newpipe.local.history

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.HistorySettingsFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.testUtil.TestDatabase

class HistoryDeletionConfirmationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private lateinit var manager: HistoryRecordManager

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        manager = HistoryRecordManager(context)
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test(timeout = 30_000)
    fun cancelPreservesWatchHistoryAndConfirmationDeletesOnlyTheSelectedEntryInThisProfile() {
        val profile = ProfileManager.getActiveProfileId(context)
        val selected = addWatchedStream("Selected video", profile)
        val untouched = addWatchedStream("Keep this video", profile)
        val otherProfile = "00000000-0000-0000-0000-000000000001"
        database.streamHistoryDAO().insert(StreamHistoryEntity(selected, OffsetDateTime.now(), 1, otherProfile))
        val entry = manager.streamStatistics.blockingFirst().single { it.streamId == selected }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = StatisticsPlaylistFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, fragment).commitNow()
                fragment.confirmDeleteEntry(entry).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                assertNotNull(database.streamHistoryDAO().getLatestEntryForProfile(profile, selected))
                assertEquals(20_000L, database.streamStateDAO().getStateForProfile(profile, selected).blockingFirst().single().progressMillis)
                fragment.confirmDeleteEntry(entry).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            manager.streamStatistics.filter { rows -> rows.none { it.streamId == selected } }
                .timeout(5, TimeUnit.SECONDS).blockingFirst()
            assertTrue(database.streamStateDAO().getStateForProfile(profile, selected).blockingFirst().isEmpty())
            assertNotNull(database.streamHistoryDAO().getLatestEntryForProfile(profile, untouched))
            assertNotNull(database.streamHistoryDAO().getLatestEntryForProfile(otherProfile, selected))
        }
    }

    @Test(timeout = 30_000)
    fun searchHistoryClearsOnlyAfterConfirmationAndThenRefreshesSuggestions() {
        database.searchHistoryDAO().insertAll(
            listOf(SearchHistoryEntry(OffsetDateTime.now(), 0, "first"), SearchHistoryEntry(OffsetDateTime.now(), 9, "second"))
        )
        val profile = ProfileManager.getActiveProfileId(context)
        val watched = addWatchedStream("Keep watch history", profile)
        val disposables = CompositeDisposable()
        val refreshed = CountDownLatch(1)
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    HistorySettingsFragment.openDeleteSearchHistoryDialog(activity, manager, disposables) { refreshed.countDown() }
                        .getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                    assertEquals(2, database.searchHistoryDAO().getAll().blockingFirst().size)
                    assertEquals(1L, refreshed.count)
                    HistorySettingsFragment.openDeleteSearchHistoryDialog(activity, manager, disposables) { refreshed.countDown() }
                        .getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                assertTrue(refreshed.await(5, TimeUnit.SECONDS))
                assertTrue(manager.getRelatedSearches("", 25, 25).blockingFirst().isEmpty())
                assertNotNull(database.streamHistoryDAO().getLatestEntryForProfile(profile, watched))
            }
        } finally {
            disposables.dispose()
        }
    }

    private fun addWatchedStream(title: String, profile: String): Long {
        val id = database.streamDAO().insert(
            StreamEntity(serviceId = 0, url = "https://example.com/${title.replace(' ', '-')}", title = title, streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Channel")
        )
        database.streamHistoryDAO().insert(StreamHistoryEntity(id, OffsetDateTime.now(), 1, profile))
        database.streamStateDAO().insert(StreamStateEntity(id, 20_000, profile))
        return id
    }
}
