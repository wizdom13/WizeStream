package org.schabi.newpipe.local.feed.service

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.pm.ServiceInfo
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.local.feed.FeedScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FeedLoadServiceLifecycleTest {
    private val scope = FeedScope(0, 1)

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).grantPermissions(application.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        FeedEventManager.reset(scope)
    }

    @After
    fun tearDown() {
        RxAndroidPlugins.reset()
        FeedEventManager.reset(scope)
    }

    @Test
    fun navigationAndPlaybackActivityLeaveRefreshRunningAndRepeatedStartsAreIgnored() {
        withRefresh { service, loader, result ->
            val intent = FeedLoadService.createLoadIntent(service, -1, scope)
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(intent, 0, 1))
            val feedScreen = Robolectric.buildActivity(Activity::class.java).setup()
            feedScreen.pause().stop().destroy()
            val playbackScreen = Robolectric.buildActivity(Activity::class.java).setup()
            assertTrue(result.hasObservers())
            service.onStartCommand(intent, 0, 2)
            playbackScreen.pause().stop().destroy()
            service.onStartCommand(intent, 0, 3)
            verify(loader, times(1)).startLoading(-1, false, scope, null)
            assertTrue(result.hasObservers())
            result.onSuccess(emptyList())
            assertTrue(shadowOf(service).isStoppedBySelf)
        }
    }

    @Test
    fun serviceDestructionCancelsOutstandingWorkAndClearsReplayedProgress() {
        withRefresh { service, loader, result ->
            service.onStartCommand(FeedLoadService.createLoadIntent(service, -1, scope), 0, 1)
            FeedEventManager.postEvent(scope, FeedEventManager.Event.ProgressEvent(1, 3))
            service.onDestroy()
            verify(loader).cancel()
            assertFalse(result.hasObservers())
            FeedEventManager.events(scope).take(1).test().assertValue(FeedEventManager.Event.IdleEvent)
        }
    }

    @Test
    fun dataSyncTimeoutStopsServiceAndKeepsNoOutstandingSubscription() {
        withRefresh { service, loader, result ->
            service.onStartCommand(FeedLoadService.createLoadIntent(service, -1, scope), 0, 1)
            service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            verify(loader).cancel()
            assertFalse(result.hasObservers())
            assertTrue(shadowOf(service).isStoppedBySelf)
        }
    }

    private fun withRefresh(test: (FeedLoadService, FeedLoadManager, SingleSubject<List<Notification<FeedUpdateInfo>>>) -> Unit) {
        val result = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        mockConstruction(FeedLoadManager::class.java) { loader, _ ->
            `when`(loader.notification).thenReturn(Flowable.never())
            `when`(loader.startLoading(-1, false, scope, null)).thenReturn(result)
        }.use { loaders ->
            val controller = Robolectric.buildService(FeedLoadService::class.java).create()
            var failure: Throwable? = null
            try {
                test(controller.get(), loaders.constructed().single(), result)
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                try {
                    controller.destroy()
                } catch (cleanupError: Throwable) {
                    if (failure == null) throw cleanupError
                    failure.addSuppressed(cleanupError)
                }
            }
        }
    }
}
