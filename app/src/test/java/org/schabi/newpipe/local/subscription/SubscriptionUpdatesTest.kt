package org.schabi.newpipe.local.subscription

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.SubscriptionSeparatorItem
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubscriptionUpdatesTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val store = ViewModelStore()
    private val clock = TestScheduler()
    private val rendered = mutableListOf<SubscriptionState>()
    private val observer = Observer<SubscriptionState> { rendered += it }
    private lateinit var database: AppDatabase
    private lateinit var model: SubscriptionViewModel
    private val dao get() = database.subscriptionDAO()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { clock }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
    }

    @After
    fun tearDown() {
        if (::model.isInitialized) model.stateLiveData.removeObserver(observer)
        store.clear()
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun unchangedDatabaseInvalidationsDoNotRebuildSubscriptionItems() {
        val first = channel("Alpha")
        val otherProfile = channel("Other", "other-profile")
        observe()
        val initial = state()
        rendered.clear()

        repeat(4) {
            dao.update(first.copy())
            tick()
        }
        dao.update(otherProfile.copy(name = "Renamed elsewhere"))
        tick()

        assertTrue(rendered.isEmpty())
        assertSame(initial, state())
    }

    @Test
    fun changedMetadataMuteStateAndMembershipStillReachTheUi() {
        var channel = channel("Alpha")
        observe()
        var previous = state()

        for (updated in listOf(
            channel.copy(name = "Renamed"),
            channel.copy(avatarUrl = "https://example.com/new-avatar.png", subscriberCount = 42),
            channel.copy(feedMuted = true)
        )) {
            dao.update(updated)
            tick()
            assertNotSame(previous, state())
            assertEquals(listOf(channel.uid), state().subscriptions.map { (it as ChannelItem).id })
            previous = state()
            channel = updated
        }

        val second = channel("Beta")
        tick()
        assertEquals(setOf(channel.uid, second.uid), state().subscriptions.map { (it as ChannelItem).id }.toSet())
        dao.deleteSubscriptionForProfile(profile, channel.serviceId, channel.url!!)
        tick()
        assertEquals(listOf(second.uid), state().subscriptions.map { (it as ChannelItem).id })
    }

    @Test
    fun savedOrderSeparatorsSearchAndProfileChangesRemainIndependentOfSnapshotDeduplication() {
        val alpha = channel("Alpha")
        val beta = channel("Beta")
        val foreign = channel("Foreign", "other-profile")
        observe()
        SubscriptionLayout(context, profile).save(
            listOf(
                SubscriptionLayout.Entry("separator:music", "Music"),
                SubscriptionLayout.Entry(SubscriptionLayout.key(beta)),
                SubscriptionLayout.Entry(SubscriptionLayout.key(alpha))
            )
        )
        tick()
        assertTrue(state().subscriptions.first() is SubscriptionSeparatorItem)
        assertEquals(listOf(beta.uid, alpha.uid), state().subscriptions.filterIsInstance<ChannelItem>().map { it.id })

        model.setFilterQuery("Beta")
        tick()
        assertEquals(listOf(beta.uid), state().subscriptions.map { (it as ChannelItem).id })
        model.setFilterQuery("")
        tick()
        assertTrue(state().subscriptions.first() is SubscriptionSeparatorItem)
        assertEquals(listOf(beta.uid, alpha.uid), state().subscriptions.filterIsInstance<ChannelItem>().map { it.id })

        model.setProfile("other-profile")
        tick()
        assertEquals(listOf(foreign.uid), state().subscriptions.map { (it as ChannelItem).id })
    }

    @Test
    fun countObserverIgnoresMetadataChangesAndOtherProfilesButTracksAddsAndRemovals() {
        val alpha = channel("Alpha")
        val counts = SubscriptionManager(context, profile).subscriptionCount().test()
        counts.assertValues(1)
        dao.update(alpha.copy(name = "Renamed", feedMuted = true))
        channel("Other", "other-profile")
        tick()
        counts.assertValues(1)

        channel("Beta")
        tick()
        dao.deleteSubscriptionForProfile(profile, alpha.serviceId, alpha.url!!)
        tick()
        counts.assertValues(1, 2, 1).assertNoErrors()
        counts.cancel()
    }

    private fun observe() {
        model = SubscriptionViewModel(context)
        store.put("subscriptions", model)
        model.stateLiveData.observeForever(observer)
        tick()
    }

    private fun tick() {
        shadowOf(Looper.getMainLooper()).idle()
        clock.advanceTimeBy(1, TimeUnit.SECONDS)
    }

    private fun state() = requireNotNull(model.stateLiveData.value) as SubscriptionState.LoadedState

    private fun channel(name: String, profileId: String = profile): SubscriptionEntity = SubscriptionEntity(
        serviceId = 0,
        url = "https://example.com/$name",
        name = name,
        profileId = profileId
    ).apply { uid = dao.insert(this) }
}
