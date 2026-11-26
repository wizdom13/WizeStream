package org.schabi.newpipe.local.subscription

import android.app.Application
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.SubscriptionLayout.Entry
import org.schabi.newpipe.local.subscription.dialog.SubscriptionLayoutDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h480dp")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubscriptionLayoutDialogTest {
    private lateinit var host: ActivityController<TestActivity>
    private lateinit var database: AppDatabase
    private lateinit var channels: List<SubscriptionEntity>
    private val io = TestScheduler()
    private val errors = mutableListOf<Throwable>()
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
    private val store get() = SubscriptionLayout(context, profile)
    private val keys get() = channels.map(SubscriptionLayout::key)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { io }
        RxJavaPlugins.setErrorHandler { errors += it }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        channels = listOf(channel("Alpha", true), channel("Bravo"), channel("Charlie", true), channel("Delta", service = 1))
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
    }

    @After
    fun tearDown() {
        try {
            if (::host.isInitialized) host.pause().stop().destroy()
        } finally {
            databaseField.set(null, previousDatabase)
            if (::database.isInitialized) database.close()
            RxJavaPlugins.reset()
            RxAndroidPlugins.reset()
        }
    }

    @Test
    fun arrangementWaitsForLoadingAndIsStagedUntilSaveWithOtherProfilesUntouched() {
        val other = SubscriptionLayout(context, "other-profile")
        val otherEntries = keys.reversed().map(::Entry)
        other.save(otherEntries)
        channels.forEach { database.subscriptionDAO().insert(it.copy(uid = 0, profileId = "other-profile", feedMuted = !it.feedMuted)) }
        var fragment = show()
        assertFalse(dialog(fragment).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertCompactActionsReachable(fragment)
        click(fragment, R.string.subscription_order_muted_bottom)
        load()
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), labels(fragment))

        click(fragment, R.string.subscription_order_muted_bottom)
        assertEquals(listOf("Bravo", "Delta", "Alpha", "Charlie"), labels(fragment))
        assertTrue(store.read().isEmpty())
        finish(fragment, AlertDialog.BUTTON_NEGATIVE)
        assertTrue(store.read().isEmpty())

        fragment = show()
        load()
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), labels(fragment))
        click(fragment, R.string.subscription_order_muted_bottom)
        finish(fragment, AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf(keys[1], keys[3], keys[0], keys[2]), store.read().map { it.key })
        assertEquals(otherEntries, other.read())
        assertEquals(channels, channels.map { database.subscriptionDAO().getSubscriptionForProfile(profile, it.uid) })
    }

    @Test
    fun pendingSectionArrangementSurvivesRecreationBeforeItIsSaved() {
        val separator = Entry("separator:favorites", "Favorites")
        val original = listOf(Entry(keys[0]), Entry(keys[1]), separator, Entry(keys[2]), Entry(keys[3]))
        store.save(original)
        val fragment = show()
        load()
        click(fragment, R.string.subscription_order_muted_bottom)
        assertEquals(listOf("Bravo", "Alpha", "Favorites", "Delta", "Charlie"), labels(fragment))
        assertEquals(original, store.read())

        host.recreate()
        settle()
        val restored = host.get().supportFragmentManager.findFragmentByTag(TAG) as SubscriptionLayoutDialog
        assertNotSame(fragment, restored)
        load()
        assertEquals(listOf("Bravo", "Alpha", "Favorites", "Delta", "Charlie"), labels(restored))
        assertEquals(original, store.read())
        finish(restored, AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf(Entry(keys[1]), Entry(keys[0]), separator, Entry(keys[3]), Entry(keys[2])), store.read())
    }

    @Test
    fun resetRestoresAlphabeticalDefaultAndANoOpActionDoesNotFreezeIt() {
        store.save(listOf(Entry(keys[3]), Entry("separator:custom", "Custom")) + keys.take(3).map(::Entry))
        var fragment = show()
        load()
        click(fragment, R.string.subscription_order_muted_bottom)
        click(fragment, R.string.subscription_order_reset)
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), labels(fragment))
        finish(fragment, AlertDialog.BUTTON_POSITIVE)
        assertTrue(store.read().isEmpty())

        database.subscriptionDAO().setFeedMutedForProfile(profile, channels.map { it.uid }, false)
        fragment = show()
        load()
        click(fragment, R.string.subscription_order_reset)
        click(fragment, R.string.subscription_order_muted_bottom)
        finish(fragment, AlertDialog.BUTTON_POSITIVE)
        assertTrue(store.read().isEmpty())
        channel("Aaron")
        fragment = show()
        load()
        assertEquals(listOf("Aaron", "Alpha", "Bravo", "Charlie", "Delta"), labels(fragment))
        finish(fragment, AlertDialog.BUTTON_NEGATIVE)
    }

    private fun channel(name: String, muted: Boolean = false, service: Int = 0): SubscriptionEntity = SubscriptionEntity(
        serviceId = service,
        url = "https://example.com/$name",
        name = name,
        feedMuted = muted,
        profileId = profile
    ).apply { uid = database.subscriptionDAO().insert(this) }

    private fun show(): SubscriptionLayoutDialog = SubscriptionLayoutDialog.newInstance(profile).apply {
        showNow(this@SubscriptionLayoutDialogTest.host.get().supportFragmentManager, TAG)
        settle()
    }

    private fun load() {
        io.triggerActions()
        settle()
        assertTrue(errors.toString(), errors.isEmpty())
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        host.get().supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun dialog(fragment: SubscriptionLayoutDialog): AlertDialog = fragment.requireDialog() as AlertDialog

    private fun button(fragment: SubscriptionLayoutDialog, label: Int): Button = views(dialog(fragment).window!!.decorView)
        .filterIsInstance<Button>().single { it.text.toString() == context.getString(label) }

    private fun click(fragment: SubscriptionLayoutDialog, label: Int) {
        button(fragment, label).performClick()
        settle()
    }

    private fun finish(fragment: SubscriptionLayoutDialog, which: Int) {
        dialog(fragment).getButton(which).performClick()
        settle()
        assertFalse(fragment.isAdded)
    }

    private fun labels(fragment: SubscriptionLayoutDialog): List<String> {
        val list = views(dialog(fragment).window!!.decorView).filterIsInstance<RecyclerView>().single()

        @Suppress("UNCHECKED_CAST")
        val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        return (0 until adapter.itemCount).map { position ->
            val holder = adapter.createViewHolder(list, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            views(holder.itemView).filterIsInstance<TextView>().single().text.toString()
        }
    }

    private fun assertCompactActionsReachable(fragment: SubscriptionLayoutDialog) {
        val window = dialog(fragment)
        val decor = window.window!!.decorView
        val metrics = context.resources.displayMetrics
        decor.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        val action = button(fragment, R.string.subscription_order_muted_bottom)
        val scroll = generateSequence(action.parent) { it.parent }.filterIsInstance<NestedScrollView>().first()
        scroll.isSmoothScrollingEnabled = false
        scroll.fullScroll(View.FOCUS_DOWN)
        settle()
        val help = views(decor).filterIsInstance<TextView>().single { it.text.toString() == context.getString(R.string.subscription_order_muted_bottom_help) }
        for (view in listOf(action, help, window.getButton(AlertDialog.BUTTON_POSITIVE), window.getButton(AlertDialog.BUTTON_NEGATIVE))) {
            val visible = Rect()
            assertTrue("Dialog action must be visible after scrolling", view.getGlobalVisibleRect(visible))
            assertEquals(view.height, visible.height())
        }
        assertFalse(views(scroll).any { it === window.getButton(AlertDialog.BUTTON_POSITIVE) })
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(views(it)) }
    }

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
        }
    }

    companion object {
        private const val TAG = "subscription_layout_test"
    }
}
