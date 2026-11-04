package org.schabi.newpipe.local.subscription

import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GroupChannelsSearchTest {
    @Test
    fun filtersLoadedGroupNamesAndClearRestoresItsChannels() = withScreen { activity, fragment, state ->
        val search = searchView(activity)
        state.value = GroupChannelsViewModel.State(channels = channels())
        assertEquals(2, itemCount(fragment))
        search.setQuery("  TRAVEL  ", false)
        assertEquals(1, itemCount(fragment))
        search.setQuery("@HIDDENHANDLE", false)
        assertEquals(1, itemCount(fragment))
        search.setQuery("hiddenhandle", false)
        assertEquals(1, itemCount(fragment))
        search.setQuery("missing", false)
        assertEquals(0, itemCount(fragment))
        assertEquals(activity.getString(R.string.search_no_results), fragment.requireView().findViewById<TextView>(R.id.empty_state_message).text)
        search.setQuery("", false)
        assertEquals(2, itemCount(fragment))
    }

    @Test
    fun databaseUpdatesKeepTheActiveFilter() = withScreen { activity, fragment, state ->
        searchView(activity).setQuery("travel", false)
        state.value = GroupChannelsViewModel.State(channels = channels())
        assertEquals(1, itemCount(fragment))
        state.value = GroupChannelsViewModel.State(channels = channels().drop(1))
        assertEquals(0, itemCount(fragment))
        assertTrue(fragment.requireView().findViewById<android.view.View>(R.id.empty_state_view).visibility == android.view.View.VISIBLE)
    }

    @Test
    fun savedQuerySurvivesFragmentRecreation() = withScreen { activity, fragment, state ->
        searchView(activity).setQuery("travel", false)
        state.value = GroupChannelsViewModel.State(channels = channels())
        val manager = activity.supportFragmentManager
        val saved = manager.saveFragmentInstanceState(fragment)
        manager.beginTransaction().remove(fragment).commitNow()
        val replacement = GroupChannelsFragment.newInstance(42, "My group").apply { setInitialSavedState(saved) }
        manager.beginTransaction().add(android.R.id.content, replacement).commitNow()
        assertEquals(1, itemCount(replacement))
        assertEquals(42L, replacement.arguments?.getLong("group_id"))
    }

    @Test
    fun searchMenuIsRemovedWhenLeavingTheGroup() = withScreen { activity, fragment, _ ->
        assertTrue(menu(activity).findItem(R.id.menu_item_search_content) != null)
        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        assertFalse(menu(activity).findItem(R.id.menu_item_search_content) != null)
    }

    private fun channels() = listOf(
        SubscriptionEntity(uid = 1, serviceId = 0, url = "https://example.org/travel", name = "Travel channel", handle = "@HiddenHandle"),
        SubscriptionEntity(uid = 2, serviceId = 0, url = "https://example.org/music", name = "Music channel")
    )

    private fun itemCount(fragment: GroupChannelsFragment) = fragment.requireView().findViewById<RecyclerView>(R.id.items_list).adapter!!.itemCount

    private fun menu(activity: AppCompatActivity): MenuBuilder = MenuBuilder(activity).also {
        activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, it)
    }

    private fun searchView(activity: AppCompatActivity) = menu(activity).findItem(R.id.menu_item_search_content).actionView as SearchView

    private fun withScreen(test: (AppCompatActivity, GroupChannelsFragment, MutableLiveData<GroupChannelsViewModel.State>) -> Unit) {
        val state = MutableLiveData(GroupChannelsViewModel.State())
        mockConstruction(SubscriptionManager::class.java).use {
            mockConstruction(GroupChannelsViewModel::class.java) { model, _ -> `when`(model.state).thenReturn(state) }.use { models ->
                val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
                val activity = controller.get()
                activity.setTheme(R.style.LightTheme)
                controller.setup()
                try {
                    val fragment = GroupChannelsFragment.newInstance(42, "My group")
                    activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
                    verify(models.constructed().single()).load(42)
                    test(activity, fragment, state)
                } finally {
                    controller.pause().stop().destroy()
                }
            }
        }
    }
}
