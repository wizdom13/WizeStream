package org.schabi.newpipe.local.cleanup

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.InfoCache

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExitCleanupTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val pending = context.getSharedPreferences("exit_cleanup_pending", Context.MODE_PRIVATE)
    private val cache = InfoCache.getInstance()
    private val profile get() = ProfileManager.getActiveProfileId(context)

    @Before
    fun setup() {
        ExitCleanup.initialize(context)
        prefs.edit().clear().commit()
        pending.edit().putString(ExitCleanup.TOKEN, "old").commit()
        cache.clearCache()
        cache.putInfo(0, "video", mock(StreamInfo::class.java), InfoCache.Type.STREAM)
        DownloaderImpl.init(OkHttpClient.Builder()).setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "captcha")
        DownloaderImpl.getInstance().setCookie("other-service", "keep")
        prefs.edit().putString(context.getString(R.string.recaptcha_cookies_key), "captcha").commit()
    }

    @Test
    fun defaultsAndCancelledOrReplacedJobsDoNotClearAnything() {
        mockConstruction(HistoryRecordManager::class.java).use { records ->
            ExitCleanup.clean(context, profile, "old")
            assertNotNull(cache.getFromKey(0, "video", InfoCache.Type.STREAM))
            prefs.edit().putBoolean(ExitCleanup.ENABLED, true).commit()
            pending.edit().putString(ExitCleanup.TOKEN, "new").commit()
            ExitCleanup.clean(context, profile, "old")
            ExitCleanup.clean(context, "another-profile", "new")
            assertNotNull(cache.getFromKey(0, "video", InfoCache.Type.STREAM))
            assertEquals("captcha", DownloaderImpl.getInstance().getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
            assertTrue(records.constructed().isEmpty())
        }
    }

    @Test
    fun metadataOnlyKeepsCookiesAndDoesNotTouchHistoryDatabase() {
        prefs.edit().putBoolean(ExitCleanup.ENABLED, true).commit()
        mockConstruction(HistoryRecordManager::class.java).use { records ->
            assertTrue(ExitCleanup.clean(context, profile, "old"))
            assertNull(cache.getFromKey(0, "video", InfoCache.Type.STREAM))
            assertEquals("captcha", DownloaderImpl.getInstance().getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
            assertTrue(records.constructed().isEmpty())
            assertNull(pending.getString(ExitCleanup.TOKEN, null))
        }
    }

    @Test
    fun cookieOptInClearsOnlyRecaptchaInMemoryAndPreferences() {
        prefs.edit().putBoolean(ExitCleanup.ENABLED, true).putBoolean(ExitCleanup.COOKIES, true).commit()
        ExitCleanup.clean(context, profile, "old")
        assertEquals("", prefs.getString(context.getString(R.string.recaptcha_cookies_key), null))
        assertEquals("", DownloaderImpl.getInstance().getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
        assertEquals("keep", DownloaderImpl.getInstance().getCookie("other-service"))
    }

    @Test
    fun positionOptInUsesOnlyTheExistingProfileScopedProgressDeletion() {
        prefs.edit().putBoolean(ExitCleanup.ENABLED, true).putBoolean(ExitCleanup.POSITIONS, true).commit()
        val expectedProfile = profile
        mockConstruction(HistoryRecordManager::class.java) { manager, construction ->
            assertEquals(expectedProfile, construction.arguments()[1])
            `when`(manager.deleteCompleteStreamStateHistory()).thenReturn(Single.just(2))
        }.use { records ->
            ExitCleanup.clean(context, expectedProfile, "old")
            val manager = records.constructed().single()
            verify(manager).deleteCompleteStreamStateHistory()
            verifyNoMoreInteractions(manager)
        }
    }

    @Test
    fun lifecycleSchedulesDelayedWorkAndReopeningCancelsItsPersistentTicket() {
        val workManager = mock(androidx.work.impl.WorkManagerImpl::class.java)
        androidx.work.impl.WorkManagerImpl.setDelegate(workManager)
        try {
            prefs.edit().putBoolean(ExitCleanup.ENABLED, true).commit()
            val activity = org.robolectric.Robolectric.buildActivity(Activity::class.java).create().start()
            activity.stop()
            val enqueue = org.mockito.Mockito.mockingDetails(workManager).invocations
                .single { it.method.name == "enqueueUniqueWork" }
            assertEquals(ExitCleanup.WORK, enqueue.arguments[0])
            assertEquals(androidx.work.ExistingWorkPolicy.REPLACE, enqueue.arguments[1])
            val request = enqueue.arguments[2] as androidx.work.OneTimeWorkRequest
            assertEquals(30_000L, request.workSpec.initialDelay)
            assertEquals(profile, request.workSpec.input.getString(ExitCleanup.PROFILE))
            val token = request.workSpec.input.getString(ExitCleanup.TOKEN)!!
            assertEquals(token, pending.getString(ExitCleanup.TOKEN, null))
            activity.start()
            assertNull(pending.getString(ExitCleanup.TOKEN, null))
            assertFalse(ExitCleanup.clean(context, profile, token))
            assertNotNull(cache.getFromKey(0, "video", InfoCache.Type.STREAM))
            activity.stop().destroy()
        } finally {
            androidx.work.impl.WorkManagerImpl.setDelegate(null)
        }
    }

    @Test
    fun leavingOneActivityDoesNotCleanWhileAnotherActivityOrPlayerIsOpen() {
        val gate = ExitCleanupGate()
        val first = mock(Activity::class.java)
        val second = mock(Activity::class.java)
        gate.started(first)
        gate.started(second)
        gate.stopped(first)
        assertNull(gate.ticket())
        gate.playerStarted()
        gate.stopped(second)
        assertNull(gate.ticket())
        gate.playerStopped()
        var cleaned = false
        assertTrue(gate.run(gate.ticket()!!) { cleaned = true })
        assertTrue(cleaned)
    }

    @Test
    fun reopeningOrStartingPlaybackInvalidatesAnAlreadyCapturedCleanupTicket() {
        val gate = ExitCleanupGate()
        val old = gate.ticket()!!
        val activity = mock(Activity::class.java)
        gate.started(activity)
        gate.stopped(activity)
        assertFalse(gate.run(old) { fail("Stale cleanup ran") })
        val beforePlayer = gate.ticket()!!
        gate.playerStarted()
        gate.playerStopped()
        assertFalse(gate.run(beforePlayer) { fail("Stale cleanup ran") })
    }
}
