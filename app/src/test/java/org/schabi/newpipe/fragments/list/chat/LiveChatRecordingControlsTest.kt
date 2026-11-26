package org.schabi.newpipe.fragments.list.chat

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.LiveChatRecorder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LiveChatRecordingControlsTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var host: ActivityController<TestActivity>
    private lateinit var scope: CoroutineScope
    private lateinit var source: Channel<Result<List<BulletCommentsInfoItem>>>
    private lateinit var recorder: LiveChatRecorder.Controller
    private lateinit var fragment: LiveChatFragment
    private var sourceStarts = 0

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        File(context.filesDir, "recorded-live-chats").deleteRecursively()
        scope = CoroutineScope(SupervisorJob() + Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        source = Channel(Channel.UNLIMITED)
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
    }

    @After
    fun tearDown() {
        IncognitoMode.setEnabled(context, true)
        idle()
        host.pause().stop().destroy()
        scope.cancel()
        idle()
    }

    @Test
    fun stopShowsSavingImmediatelyAndTheRecreatedViewShowsResumeWhenDelayedSaveCompletes() {
        val gate = CompletableDeferred<Unit>()
        mount(object : LiveChatRecorder.ArchiveStore() {
            override suspend fun save(context: Context, key: LiveChatRecorder.Key, archive: LiveChatRecorder.Archive, allowed: () -> Boolean): Boolean {
                gate.await()
                return super.save(context, key, archive, allowed)
            }
        })
        assertButton(R.string.live_chat_capture_start)
        assertEquals(0, sourceStarts)
        capture().performClick()
        idle()
        assertButton(R.string.live_chat_capture_stop)
        send("first")

        val oldButton = capture()
        oldButton.performClick()
        assertButton(R.string.live_chat_capture_saving, enabled = false)
        idle()
        assertButton(R.string.live_chat_capture_saving, enabled = false)
        val manager = host.get().supportFragmentManager
        manager.beginTransaction().detach(fragment).commitNow()
        idle()
        gate.complete(Unit)
        idle()
        assertEquals(context.getString(R.string.live_chat_capture_saving), oldButton.text.toString())

        manager.beginTransaction().attach(fragment).commitNow()
        idle()
        assertButton(R.string.live_chat_capture_resume)
        assertEquals(context.getString(R.string.live_chat_saved_badge), fragment.requireView().findViewById<Button>(R.id.liveChatSaved).text.toString())
        assertEquals(1, sourceStarts)
        capture().performClick()
        idle()
        assertEquals(2, sourceStarts)
        send("second")
        capture().performClick()
        idle()
        assertButton(R.string.live_chat_capture_resume)
        assertEquals(listOf("first", "second"), LiveChatRecorder.read(context, SERVICE, URL).map(LiveChatRecorder.Entry::text))
    }

    @Test
    fun newFragmentObservesAnExistingRecordingWithoutStartingAnotherCollector() {
        mount()
        capture().performClick()
        idle()
        send("recorded")
        val replacement = newFragment()
        host.get().supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, replacement).commitNow()
        fragment = replacement
        idle()

        assertButton(R.string.live_chat_capture_stop)
        assertEquals(1, sourceStarts)
        capture().performClick()
        assertButton(R.string.live_chat_capture_saving, enabled = false)
        idle()
        assertButton(R.string.live_chat_capture_resume)
        assertEquals(1, sourceStarts)
    }

    @Test
    fun saveFailureOffersRetrySavingAndSuccessfulRetryChangesTheButtonWithoutAnotherTap() {
        var fail = true
        mount(object : LiveChatRecorder.ArchiveStore() {
            override suspend fun save(context: Context, key: LiveChatRecorder.Key, archive: LiveChatRecorder.Archive, allowed: () -> Boolean): Boolean {
                if (fail) throw IOException("Unavailable")
                return super.save(context, key, archive, allowed)
            }
        })
        capture().performClick()
        idle()
        send("keep me")
        capture().performClick()
        idle()

        assertButton(R.string.live_chat_capture_retry_save)
        assertFalse(LiveChatRecorder.hasSaved(context, SERVICE, URL))
        fail = false
        capture().performClick()
        assertButton(R.string.live_chat_capture_saving, enabled = false)
        idle()
        assertButton(R.string.live_chat_capture_resume)
        assertEquals(listOf("keep me"), LiveChatRecorder.read(context, SERVICE, URL).map(LiveChatRecorder.Entry::text))
        assertEquals(1, sourceStarts)
    }

    private fun mount(store: LiveChatRecorder.ArchiveStore = LiveChatRecorder.ArchiveStore()) {
        recorder = LiveChatRecorder.Controller(scope, { _, _ ->
            sourceStarts++
            source.receiveAsFlow()
        }, store = store)
        fragment = newFragment()
        host.get().supportFragmentManager.beginTransaction().add(R.id.settings_fragment_holder, fragment).commitNow()
        idle()
    }

    @Suppress("DEPRECATION")
    private fun newFragment() = LiveChatFragment.newInstance(SERVICE, URL).apply {
        recordingController = recorder
        // The recording controls are independent of visible chat polling; avoid a real extractor.
        userVisibleHint = false
    }

    private fun send(text: String) {
        val item = BulletCommentsInfoItem(SERVICE, URL, "A").apply {
            commentText = text
            duration = Duration.ZERO
        }
        assertTrue(source.trySend(Result.success(listOf(item))).isSuccess)
        idle()
    }

    private fun capture(): Button = fragment.requireView().findViewById(R.id.liveChatCapture)

    private fun assertButton(label: Int, enabled: Boolean = true) {
        assertEquals(context.getString(label), capture().text.toString())
        assertEquals(enabled, capture().isEnabled)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = R.id.settings_fragment_holder })
        }
    }

    companion object {
        private const val SERVICE = 0
        private const val URL = "https://example.com/live/controls"
    }
}
