/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.service

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshState.Finished
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshState.Outcome
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChannelFeedRefreshViewModelTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()
    private val store = ViewModelStore()
    private val loaders = ArrayDeque<Pair<String, FeedLoadManager>>()
    private lateinit var model: ChannelFeedRefreshViewModel

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear().commit()
        ProfileManager.getActiveProfileId(application)
        model = ChannelFeedRefreshViewModel(application) { profile ->
            val (expectedProfile, loader) = loaders.removeFirst()
            assertEquals(expectedProfile, profile)
            loader
        }
        store.put("refresh", model)
    }

    @After
    fun tearDown() {
        store.clear()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun sameChannelCannotStartTwiceWhileRunningEvenAfterItsModesChange() {
        val request = request()
        val pending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        val loader = enqueue(request, pending)

        assertTrue(model.refresh(request))
        assertFalse(model.refresh(request))
        assertFalse(model.refresh(request.copy(youtubeModeMask = 2)))
        assertTrue(state(request) is ChannelFeedRefreshState.Running)
        verify(loader, times(1)).startLoadingSubscription(request)
        assertTrue(pending.hasObservers())
    }

    @Test
    fun finishingOneChannelDoesNotClearAnotherChannelsRun() {
        val first = request()
        val second = request(id = 2)
        val firstPending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        val secondPending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        enqueue(first, firstPending)
        enqueue(second, secondPending)
        model.refresh(first)
        model.refresh(second)

        firstPending.onSuccess(listOf(Notification.createOnNext(info(first))))

        assertEquals(Outcome.SUCCESS, (state(first) as Finished).outcome)
        assertTrue(state(second) is ChannelFeedRefreshState.Running)
        assertTrue(secondPending.hasObservers())
    }

    @Test
    fun successfulEmptyPartialFailedAndUnavailableRefreshesHaveDifferentFeedback() {
        val error = IOException("Network unavailable")
        val outcomes = listOf(
            listOf(Notification.createOnNext(info(request()))) to Outcome.SUCCESS,
            listOf(Notification.createOnNext(info(request(), listOf(error)))) to Outcome.PARTIAL,
            listOf(Notification.createOnError<FeedUpdateInfo>(error)) to Outcome.FAILED,
            listOf(Notification.createOnComplete<FeedUpdateInfo>()) to Outcome.UNAVAILABLE
        )
        for ((results, expected) in outcomes) {
            val pending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
            enqueue(request(), pending)
            assertTrue(model.refresh(request()))
            pending.onSuccess(results)
            val finished = state(request()) as Finished
            assertEquals(expected, finished.outcome)
            assertEquals(if (expected == Outcome.PARTIAL || expected == Outcome.FAILED) listOf(error) else emptyList(), finished.errors)
            model.consume(finished)
            assertTrue(model.states.value.orEmpty().isEmpty())
        }
    }

    @Test
    fun anOldViewCannotConsumeTheResultOfANewerRefresh() {
        val request = request()
        val first = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        enqueue(request, first)
        model.refresh(request)
        first.onSuccess(listOf(Notification.createOnComplete()))
        val oldResult = state(request) as Finished

        val second = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        enqueue(request, second)
        model.refresh(request)
        model.consume(oldResult)
        assertTrue(state(request) is ChannelFeedRefreshState.Running)
        second.onSuccess(listOf(Notification.createOnComplete()))
        val newResult = state(request) as Finished
        model.consume(oldResult)
        assertSame(newResult, state(request))
    }

    @Test
    fun changingProfileCancelsOldWorkAndRejectsStalePages() {
        val original = request()
        val pending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        val loader = enqueue(original, pending)
        model.refresh(original)

        val other = ProfileManager.createProfile(application, "Other")!!
        shadowOf(Looper.getMainLooper()).idle()

        verify(loader).cancel()
        assertFalse(pending.hasObservers())
        assertTrue(model.states.value.orEmpty().isEmpty())
        assertFalse(model.refresh(original))
        pending.onSuccess(listOf(Notification.createOnNext(info(original))))
        assertTrue(model.states.value.orEmpty().isEmpty())

        val current = request(profile = other.id)
        enqueue(current, SingleSubject.create())
        assertTrue(model.refresh(current))
        assertEquals(current, state(current)?.request)
    }

    @Test
    fun clearingTheActivityStoreCancelsOutstandingWork() {
        val request = request()
        val pending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        val loader = enqueue(request, pending)
        model.refresh(request)

        store.clear()

        verify(loader).cancel()
        assertFalse(pending.hasObservers())
        assertFalse(model.refresh(request))
    }

    @Test
    fun failuresReportedOutsideTheExtractorResultAlsoFinishTheRun() {
        val request = request()
        val pending = SingleSubject.create<List<Notification<FeedUpdateInfo>>>()
        enqueue(request, pending)
        model.refresh(request)
        val failure = IOException("Unable to save the feed")
        pending.onError(failure)

        val finished = state(request) as Finished
        assertEquals(Outcome.FAILED, finished.outcome)
        assertEquals(listOf(failure), finished.errors)
        enqueue(request, SingleSubject.create())
        assertTrue(model.refresh(request))
    }

    private fun request(id: Long = 1, profile: String = ProfileManager.DEFAULT_PROFILE_ID) = ChannelFeedRefreshRequest(
        profile,
        id,
        0,
        "https://www.youtube.com/channel/Channel$id",
        1
    )

    private fun enqueue(
        request: ChannelFeedRefreshRequest,
        pending: SingleSubject<List<Notification<FeedUpdateInfo>>>
    ): FeedLoadManager = mock(FeedLoadManager::class.java).also { loader ->
        `when`(loader.startLoadingSubscription(request)).thenReturn(pending)
        loaders += request.profileId to loader
    }

    private fun state(request: ChannelFeedRefreshRequest) = model.states.value?.get(
        ChannelFeedRefreshKey(request.profileId, request.subscriptionId)
    )

    private fun info(request: ChannelFeedRefreshRequest, errors: List<Throwable> = emptyList()) = FeedUpdateInfo(
        request.subscriptionId, 0, "", "Channel", null, request.url, request.serviceId,
        request.youtubeModeMask, null, null, emptyList(), errors
    )
}
