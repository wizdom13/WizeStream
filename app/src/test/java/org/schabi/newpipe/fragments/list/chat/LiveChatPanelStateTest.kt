package org.schabi.newpipe.fragments.list.chat

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
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
import org.robolectric.shadows.ShadowPopupMenu
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LiveChatPanelStateTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var host: ActivityController<TestActivity>
    private lateinit var fragment: LiveChatFragment
    private lateinit var source: Channel<Result<List<BulletCommentsInfoItem>>>
    private var retries = 0

    @Before
    fun showChat() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        source = Channel(Channel.UNLIMITED)
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
        fragment = LiveChatFragment.newInstance(0, "https://example.com/live/panel").apply {
            messageSource = { _, _ -> source.receiveAsFlow() }
            retrySource = { _, _ -> retries++ }
        }
        host.get().supportFragmentManager.beginTransaction()
            .add(R.id.settings_fragment_holder, fragment).commitNow()
        idle()
    }

    @After
    fun closeChat() {
        host.pause().stop().destroy()
        source.close()
        idle()
    }

    @Test
    fun retryIsShownForFailuresAndHiddenAgainAfterSuccessfulMessages() {
        assertEquals(View.GONE, retry().visibility)
        send(Result.success(listOf(message("first"))))
        assertEquals(context.getString(R.string.live_chat_read_only), status().text)
        assertEquals(View.GONE, retry().visibility)

        send(Result.failure(IllegalStateException("Offline fixture")))
        assertEquals(View.VISIBLE, retry().visibility)
        assertEquals(context.getString(R.string.retry), retry().text)
        retry().performClick()
        idle()
        assertEquals(1, retries)
        assertEquals(View.GONE, retry().visibility)

        send(Result.success(listOf(message("second"))))
        assertEquals(2, fragment.requireView().findViewById<ListView>(R.id.liveChatMessages).count)
        assertEquals(View.GONE, retry().visibility)
    }

    @Test
    fun menuKeepsSavedChatAndDanmakuAvailableAndOffersReturnFromOverlay() {
        send(Result.success(listOf(message("fixture"))))
        fragment.requireView().findViewById<View>(R.id.liveChatMore).performClick()
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        assertTrue(popup.menu.findItem(R.id.liveChatSaved).isEnabled)
        assertTrue(popup.menu.findItem(R.id.liveChatDanmaku).isEnabled)
        popup.menu.performIdentifierAction(R.id.liveChatDanmaku, 0)
        idle()

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue(preferences.getBoolean(context.getString(R.string.danmaku_enabled_key), false))
        assertEquals(context.getString(R.string.live_chat_overlay_active), status().text)
        assertEquals(View.VISIBLE, retry().visibility)
        assertEquals(context.getString(R.string.live_chat_use_list), retry().text)
        fragment.requireView().findViewById<View>(R.id.liveChatMore).performClick()
        assertFalse(ShadowPopupMenu.getLatestPopupMenu().menu.findItem(R.id.liveChatDanmaku).isEnabled)
        ShadowPopupMenu.getLatestPopupMenu().dismiss()

        retry().performClick()
        idle()
        assertFalse(preferences.getBoolean(context.getString(R.string.danmaku_enabled_key), true))
        send(Result.success(emptyList()))
        assertEquals(context.getString(R.string.live_chat_read_only), status().text)
        assertEquals(View.GONE, retry().visibility)
    }

    private fun send(result: Result<List<BulletCommentsInfoItem>>) {
        assertTrue(source.trySend(result).isSuccess)
        idle()
    }

    private fun message(text: String) = BulletCommentsInfoItem(0, "https://example.com/live/panel", "Fixture").apply {
        commentText = text
    }

    private fun retry(): Button = fragment.requireView().findViewById(R.id.liveChatRetry)

    private fun status(): TextView = fragment.requireView().findViewById(R.id.liveChatStatus)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = R.id.settings_fragment_holder })
        }
    }
}
