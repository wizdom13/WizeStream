package org.schabi.newpipe.settings

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.settings.InvidiousInstanceDirectory.ResponseFailure

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InvidiousDirectoryFailureTest {
    private lateinit var host: ActivityController<TestActivity>
    private lateinit var fragment: TestFragment
    private val context get() = host.get()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()

    @Before
    fun setUp() {
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
        fragment = TestFragment()
        context.supportFragmentManager.beginTransaction().add(fragment, "directory_error").commitNow()
    }

    @After
    fun tearDown() {
        try {
            host.pause().stop().destroy()
        } finally {
            NewPipe.setupLocalization(previousLocalization, previousCountry)
            RxJavaPlugins.reset()
            RxAndroidPlugins.reset()
        }
    }

    @Test
    fun distinguishesWrappedNetworkFailuresAndResponseProblemsWithoutCopyingRawData() {
        val cases = listOf(
            UnknownHostException("private-host.example") to R.string.error_unknown_host,
            SocketTimeoutException("session=secret") to R.string.error_timeout,
            SSLHandshakeException("private certificate") to R.string.error_ssl_exception,
            ReCaptchaException("challenge=secret", "https://private.example/") to R.string.invidious_directory_error_limited,
            ResponseFailure.Invalid(ResponseFailure.Reason.HTML) to R.string.invidious_directory_error_html,
            ResponseFailure.Invalid(ResponseFailure.Reason.INVALID_JSON) to R.string.invidious_directory_error_response,
            ResponseFailure.Invalid(ResponseFailure.Reason.TOO_LARGE) to R.string.invidious_directory_error_size
        )
        for ((error, message) in cases) {
            val description = InvidiousDirectoryFailure.describe(context, IOException("token=secret", error))
            assertTrue(description.message.startsWith(context.getString(message)))
            assertTrue(description.report.contains("URL: https://api.invidious.io/instances.json"))
            assertTrue(description.report.contains(error.javaClass.name))
            assertFalse(description.report.contains("secret"))
            assertFalse(description.report.contains("private"))
        }
        val http = InvidiousDirectoryFailure.describe(context, ResponseFailure.Http(503))
        assertTrue(http.message.contains("503"))
        assertTrue(http.report.contains("HTTP: 503"))
    }

    @Test
    fun copyKeepsFailureVisibleAndOnlyExplicitRetryStartsAnotherRequest() {
        var retries = 0
        val error = ResponseFailure.Http(429)
        var dialog = requireNotNull(InvidiousDirectoryFailure.show(fragment, error) { retries++ })
        assertTrue(dialog.findViewById<TextView>(android.R.id.message)!!.text.contains("429"))
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        settle()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().contains("HTTP: 429"))
        assertTrue(dialog.isShowing)
        assertEquals(0, retries)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        settle()
        assertFalse(dialog.isShowing)
        assertEquals(0, retries)

        dialog = requireNotNull(InvidiousDirectoryFailure.show(fragment, error) { retries++ })
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        settle()
        assertFalse(dialog.isShowing)
        assertEquals(1, retries)
    }

    @Test
    fun leavingTheSettingsViewDismissesTheDialogAndPreventsStaleRetries() {
        var retries = 0
        val error = UnknownHostException()
        val dialog = requireNotNull(InvidiousDirectoryFailure.show(fragment, error) { retries++ })
        val retry = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        context.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        settle()
        assertFalse(dialog.isShowing)
        retry.performClick()
        settle()
        assertEquals(0, retries)
        assertNull(InvidiousDirectoryFailure.show(fragment, error) { retries++ })
    }

    @Test
    fun returningToSettingsAfterAPendingDirectoryRequestRestoresTheAction() {
        val io = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { io }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        var requests = 0
        val downloader = object : Downloader() {
            override fun execute(request: Request): Response {
                requests++
                assertEquals(InvidiousInstanceDirectory.DIRECTORY_URL, request.url())
                return Response(200, "OK", emptyMap(), "[]", "[]".toByteArray(), request.url())
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw AssertionError("Unexpected async directory request")
        }
        Mockito.mockStatic(NewPipe::class.java, Mockito.CALLS_REAL_METHODS).use { newPipe ->
            newPipe.`when`<Downloader> { NewPipe.getDownloader() }.thenReturn(downloader)
            val settings = ContentSettingsFragment()
            val manager = context.supportFragmentManager
            manager.beginTransaction().add(R.id.settings_fragment_holder, settings).commitNow()
            settle()
            val key = context.getString(R.string.invidious_directory_key)
            val action = requireNotNull(settings.findPreference<Preference>(key))
            val summary = context.getString(R.string.invidious_directory_summary)
            action.performClick()
            assertFalse(action.isEnabled)
            assertEquals(context.getString(R.string.invidious_directory_loading), action.summary)

            // SettingsActivity keeps this fragment on the back stack while replacing its view.
            manager.beginTransaction().replace(R.id.settings_fragment_holder, TestFragment())
                .addToBackStack(null).commit()
            manager.executePendingTransactions()
            settle()
            assertFalse(settings.isAdded)
            assertNull(settings.view)
            assertTrue(action.isEnabled)
            assertEquals(summary, action.summary)
            io.triggerActions()
            settle()
            assertEquals("Leaving the view must cancel the pending request", 0, requests)

            assertTrue(manager.popBackStackImmediate())
            settle()
            assertSame(settings, manager.findFragmentById(R.id.settings_fragment_holder))
            assertSame(action, settings.findPreference<Preference>(key))
            assertTrue(action.isEnabled)
            assertEquals(summary, action.summary)
            action.performClick()
            assertFalse(action.isEnabled)
            io.triggerActions()
            settle()
            assertEquals(1, requests)
            assertTrue(action.isEnabled)
            assertEquals(summary, action.summary)
        }
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = R.id.settings_fragment_holder })
        }
    }

    class TestFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext())
    }
}
