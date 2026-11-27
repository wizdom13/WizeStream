/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.service

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.SerialDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.profiles.ProfileManager

data class ChannelFeedRefreshKey(val profileId: String, val subscriptionId: Long)

sealed class ChannelFeedRefreshState {
    abstract val request: ChannelFeedRefreshRequest

    data class Running(override val request: ChannelFeedRefreshRequest) : ChannelFeedRefreshState()

    data class Finished(
        override val request: ChannelFeedRefreshRequest,
        val outcome: Outcome,
        val errors: List<Throwable> = emptyList()
    ) : ChannelFeedRefreshState()

    enum class Outcome { SUCCESS, PARTIAL, FAILED, UNAVAILABLE }
}

/** Keeps explicit refreshes across view recreation without retaining channel views or activities. */
class ChannelFeedRefreshViewModel internal constructor(
    application: Application,
    private val newLoader: (String) -> FeedLoadManager
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { FeedLoadManager(application, it) })

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeRuns = mutableMapOf<ChannelFeedRefreshKey, Run>()
    private val mutableStates = MutableLiveData<Map<ChannelFeedRefreshKey, ChannelFeedRefreshState>>(emptyMap())
    val states: LiveData<Map<ChannelFeedRefreshKey, ChannelFeedRefreshState>> = mutableStates
    private var cleared = false

    private val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                discardOtherProfiles()
            } else {
                mainHandler.post { discardOtherProfiles() }
            }
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(profileListener)
    }

    /** Returns false when the same channel is already running or the page belongs to an old profile. */
    @MainThread
    fun refresh(request: ChannelFeedRefreshRequest): Boolean {
        if (cleared || request.profileId != ProfileManager.getActiveProfileId(getApplication())) return false
        val key = request.key()
        if (key in activeRuns) return false

        val loader = try {
            newLoader(request.profileId)
        } catch (error: Exception) {
            publish(ChannelFeedRefreshState.Finished(request, ChannelFeedRefreshState.Outcome.FAILED, listOf(error)))
            return true
        }
        val run = Run(loader)
        activeRuns[key] = run
        publish(ChannelFeedRefreshState.Running(request))
        run.disposable.set(
            Single.defer { loader.startLoadingSubscription(request) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { results -> finish(key, run, outcome(request, results)) },
                    { error ->
                        finish(
                            key,
                            run,
                            ChannelFeedRefreshState.Finished(request, ChannelFeedRefreshState.Outcome.FAILED, listOf(error))
                        )
                    }
                )
        )
        return true
    }

    /** An old view must not consume a newer refresh result for the same channel. */
    @MainThread
    fun consume(result: ChannelFeedRefreshState.Finished) {
        val key = result.request.key()
        if (mutableStates.value?.get(key) === result) {
            mutableStates.value = mutableStates.value.orEmpty() - key
        }
    }

    private fun finish(key: ChannelFeedRefreshKey, run: Run, result: ChannelFeedRefreshState.Finished) {
        if (cleared || activeRuns[key] !== run) return
        activeRuns.remove(key)
        if (result.request.profileId == ProfileManager.getActiveProfileId(getApplication())) {
            publish(result)
        } else {
            mutableStates.value = mutableStates.value.orEmpty() - key
        }
    }

    private fun publish(state: ChannelFeedRefreshState) {
        mutableStates.value = mutableStates.value.orEmpty() + (state.request.key() to state)
    }

    private fun discardOtherProfiles() {
        if (cleared) return
        val currentProfile = ProfileManager.getActiveProfileId(getApplication())
        activeRuns.keys.filter { it.profileId != currentProfile }.forEach { key ->
            activeRuns.remove(key)?.cancel()
        }
        mutableStates.value = mutableStates.value.orEmpty().filterKeys { it.profileId == currentProfile }
    }

    override fun onCleared() {
        cleared = true
        preferences.unregisterOnSharedPreferenceChangeListener(profileListener)
        activeRuns.values.forEach(Run::cancel)
        activeRuns.clear()
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }

    private class Run(val loader: FeedLoadManager, val disposable: SerialDisposable = SerialDisposable()) {
        fun cancel() {
            loader.cancel()
            disposable.dispose()
        }
    }

    private fun ChannelFeedRefreshRequest.key() = ChannelFeedRefreshKey(profileId, subscriptionId)

    private fun outcome(
        request: ChannelFeedRefreshRequest,
        results: List<Notification<FeedUpdateInfo>>
    ): ChannelFeedRefreshState.Finished {
        val errors = results.flatMap { result ->
            result.value?.errors ?: listOfNotNull(result.error)
        }
        val outcome = when {
            results.any { it.isOnNext } -> if (errors.isEmpty()) {
                ChannelFeedRefreshState.Outcome.SUCCESS
            } else {
                ChannelFeedRefreshState.Outcome.PARTIAL
            }

            errors.isNotEmpty() -> ChannelFeedRefreshState.Outcome.FAILED

            else -> ChannelFeedRefreshState.Outcome.UNAVAILABLE
        }
        return ChannelFeedRefreshState.Finished(request, outcome, errors)
    }
}
