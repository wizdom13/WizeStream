package org.schabi.newpipe.local.bookmark

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.error.ErrorPanelHelper
import org.schabi.newpipe.learning.LearningContentManager
import org.schabi.newpipe.learning.LearningMode
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.HistorySyncRecorder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BookmarkResumeRenderingTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var database: AppDatabase
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var fragment: BookmarkFragment
    private lateinit var adapter: LocalItemListAdapter
    private lateinit var root: View
    private var invalidations = 0
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        activityController.get().setTheme(R.style.LightTheme)
        activityController.setup()
        val activity = activityController.get()
        root = LayoutInflater.from(activity).inflate(R.layout.fragment_bookmarks, null)
        fragment = spy(BookmarkFragment())
        doReturn(activity).`when`(fragment).getContext()
        doReturn(root).`when`(fragment).getView()
        fragment.useAsFrontPage(true)
        fragment.categoryProfileId = ProfileManager.DEFAULT_PROFILE_ID
        fragment.field("activity", activity)
        fragment.field("errorPanelHelper", mock(ErrorPanelHelper::class.java))
        installAdapter(listOf(playlist(1, "First"), playlist(2, "Second")))
        fragment.onResume()
        invalidations = 0
    }

    @After
    fun tearDown() {
        fragment.onDestroyView()
        activityController.pause().stop().destroy()
        databaseField.set(null, previousDatabase)
        database.close()
    }

    @Test
    fun unchangedTabReturnsKeepRowsSelectionAndCurrentScrollWithoutAdapterInvalidation() {
        adapter.swapItems(0, 1)
        adapter.setSelectionToggle { }
        fragment.editMode = true
        invalidations = 0

        repeat(5) {
            fragment.onPause()
            fragment.onResume()
        }

        assertEquals(0, invalidations)
        assertEquals(listOf(2L, 1L), displayed().map { it.uid })
        assertTrue(adapter.isSelecting)
        assertTrue(fragment.editMode)
        // The retained view already has the position. A later data update must not restore
        // a stale snapshot captured before the user resumed and scrolled further.
        assertNull(fragment.itemsListState)
    }

    @Test
    fun learningUpdatesAfterReturningKeepTheUnfilteredDragOrder() {
        adapter.swapItems(0, 1)
        fragment.onPause()
        fragment.onResume()

        // Exercise the render used by the independent Learning source observer.
        fragment.field("learningSources", setOf(LearningContentManager.localPlaylistSourceId(1)))
        BookmarkFragment::class.java.getDeclaredMethod("showFilteredPlaylists").apply {
            isAccessible = true
        }.invoke(fragment)

        assertEquals(listOf(2L, 1L), displayed().map { it.uid })
    }

    @Test
    fun realPlaylistMetadataUpdatesStillRenderAfterAnUnchangedResume() {
        fragment.onPause()
        fragment.onResume()
        fragment.handleResult(listOf(playlist(1, "Renamed", count = 7), playlist(2, "Second")))

        assertTrue(invalidations > 0)
        assertEquals("Renamed", displayed().first().orderingName)
        assertEquals(7L, (displayed().first() as PlaylistMetadataEntry).streamCount)
    }

    @Test
    fun categoryChangesWhileAwayRefreshMembershipAndDeletedSelectionsReturnToAll() {
        val categories = PlaylistCategories().apply {
            rename("music", "Music")
            assign("local:1", "music")
        }
        fragment.selectedCategory = "music"
        preferences.edit().putString(PlaylistCategories.preferenceKey(fragment.categoryProfileId), categories.toJson()).commit()
        fragment.onResume()
        assertEquals(listOf(1L), displayed().map { it.uid })

        categories.assign("local:2", "music")
        preferences.edit().putString(PlaylistCategories.preferenceKey(fragment.categoryProfileId), categories.toJson()).commit()
        fragment.onResume()
        assertEquals(listOf(1L, 2L), displayed().map { it.uid })

        categories.delete("music")
        preferences.edit().putString(PlaylistCategories.preferenceKey(fragment.categoryProfileId), categories.toJson()).commit()
        fragment.onResume()
        assertEquals(PlaylistCategories.ALL, fragment.selectedCategory)
        assertEquals(listOf(1L, 2L), displayed().map { it.uid })
    }

    @Test
    fun searchStillUpdatesAndClearingItPreservesTheUnfilteredOrder() {
        adapter.swapItems(0, 1)
        fragment.onPause()
        fragment.onResume()
        fragment.setContextualSearchQuery("First")
        assertEquals(listOf(1L), displayed().map { it.uid })

        fragment.setContextualSearchQuery("")
        assertEquals(listOf(2L, 1L), displayed().map { it.uid })
    }

    @Test
    fun disablingLearningWhileAwayInvalidatesItsCategoryEvenWithoutCategoryJsonChanges() {
        preferences.edit().putBoolean(context.getString(R.string.learning_mode_key), true)
            .putBoolean(LearningMode.profilePreferenceKey(fragment.categoryProfileId), true).commit()
        fragment.selectedCategory = PlaylistOpeningPreferences.LEARNING_CATEGORY
        fragment.field("learningSources", setOf(LearningContentManager.localPlaylistSourceId(1)))
        fragment.onResume()
        assertEquals(PlaylistOpeningPreferences.LEARNING_CATEGORY, fragment.selectedCategory)

        preferences.edit().putBoolean(LearningMode.profilePreferenceKey(fragment.categoryProfileId), false).commit()
        fragment.onResume()
        assertEquals(PlaylistCategories.ALL, fragment.selectedCategory)
        assertFalse(displayed().isEmpty())
    }

    @Test
    fun recreatedViewRefreshesEvenWhenPreferencesAreUnchanged() {
        fragment.onDestroyView()
        installAdapter(listOf(playlist(1, "First")))
        fragment.field("errorPanelHelper", mock(ErrorPanelHelper::class.java))
        invalidations = 0

        fragment.onResume()

        assertTrue(invalidations > 0)
        assertEquals(listOf(1L), displayed().map { it.uid })
    }

    @Test
    fun changedLayoutManagerStillRestoresTheSavedPositionOnResume() {
        fragment.onPause()
        val savedPosition = fragment.itemsListState
        val replacement = spy(LinearLayoutManager(activityController.get()))
        root.findViewById<RecyclerView>(R.id.items_list).layoutManager = replacement
        invalidations = 0

        fragment.onResume()

        verify(replacement).onRestoreInstanceState(savedPosition)
        assertTrue(invalidations > 0)
        assertNull(fragment.itemsListState)
    }

    private fun installAdapter(items: List<PlaylistLocalItem>) {
        // History sync uses AndroidKeyStore, which is unavailable in this JVM fixture.
        // Replace only that constructor dependency; keep the adapter and database real.
        val recorder = mock(HistorySyncRecorder::class.java)
        adapter = mockStatic(HistorySyncRecorder::class.java) { recorder }.use {
            LocalItemListAdapter(activityController.get())
        }
        adapter.addItems(items)
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                invalidations++
            }
        })
        val list = root.findViewById<RecyclerView>(R.id.items_list)
        list.layoutManager = LinearLayoutManager(activityController.get())
        fragment.field("itemsList", list)
        fragment.field("itemListAdapter", adapter)
        fragment.field("completePlaylists", items)
    }

    private fun displayed() = adapter.itemsList.filterIsInstance<PlaylistLocalItem>()

    private fun playlist(id: Long, title: String, count: Long = 0) = PlaylistMetadataEntry(id, title, null, id, false, null, count)

    private fun Any.field(name: String, value: Any?) {
        var type: Class<*>? = javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == name }
            if (field != null) {
                field.isAccessible = true
                field.set(this, value)
                return
            }
            type = type.superclass
        }
        error("No field $name")
    }
}
