/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.channel

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshKey
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshRequest
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshState
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshViewModel
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager

/** Adds an explicit feed operation without changing ordinary channel-page reloads. */
class ChannelFeedRefreshMenu private constructor(private val fragment: Fragment) : MenuProvider {
    private val context = fragment.requireContext().applicationContext
    private val profileId = ProfileManager.getActiveProfileId(context)
    private val owner = fragment.viewLifecycleOwner
    private val viewModel = ViewModelProvider(fragment.requireActivity())[ChannelFeedRefreshViewModel::class.java]
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playerSheet = fragment.requireActivity().findViewById<ViewGroup>(R.id.fragment_player_holder)
    private val playerBehavior = (playerSheet?.layoutParams as? CoordinatorLayout.LayoutParams)
        ?.behavior as? BottomSheetBehavior<*>
    private var playerState = playerBehavior?.state ?: BottomSheetBehavior.STATE_HIDDEN
    private var channel: ChannelIdentity? = null
    private var subscription: SubscriptionEntity? = null
    private var subscriptionDisposable: Disposable? = null
    private var menuItem: MenuItem? = null
    private var progressSnackbar: Snackbar? = null
    private var resultSnackbar: Snackbar? = null
    private var runningRequest: ChannelFeedRefreshRequest? = null
    private var destroyed = false

    private val playerCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (destroyed) return
            playerState = newState
            render(viewModel.states.value.orEmpty())
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    }

    private val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                clearStaleProfile()
            } else {
                mainHandler.post(::clearStaleProfile)
            }
        }
    }

    init {
        (fragment.requireActivity() as MenuHost).addMenuProvider(this, owner, Lifecycle.State.RESUMED)
        viewModel.states.observe(owner, ::render)
        preferences.registerOnSharedPreferenceChangeListener(profileListener)
        playerBehavior?.addBottomSheetCallback(playerCallback)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (isCurrentProfile() && subscriptionDisposable?.isDisposed == true) {
                    channel?.let(::observeSubscription)
                }
                render(viewModel.states.value.orEmpty())
            }

            override fun onPause(owner: LifecycleOwner) = dismissFeedback()

            override fun onDestroy(owner: LifecycleOwner) {
                destroyed = true
                subscriptionDisposable?.dispose()
                preferences.unregisterOnSharedPreferenceChangeListener(profileListener)
                playerBehavior?.removeBottomSheetCallback(playerCallback)
                mainHandler.removeCallbacksAndMessages(null)
                dismissFeedback()
                menuItem = null
                subscription = null
                runningRequest = null
            }
        })
    }

    /** Bind the extractor's canonical URL, which may differ from the URL used to open the page. */
    fun bind(info: ChannelInfo) {
        if (destroyed || !isCurrentProfile()) return
        val identity = ChannelIdentity(info.serviceId, info.url)
        if (channel == identity) return

        channel = identity
        subscriptionDisposable?.dispose()
        subscription = null
        runningRequest = null
        dismissFeedback()
        updateMenu()
        observeSubscription(identity)
    }

    private fun observeSubscription(identity: ChannelIdentity) {
        subscriptionDisposable = SubscriptionManager(context, profileId)
            .getSubscriptionFlowable(identity.serviceId, identity.url)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { subscriptions ->
                    if (destroyed || channel != identity || !isCurrentProfile()) return@subscribe
                    // The shared DAO query uses LIKE; the explicit operation requires exact identity.
                    subscription = subscriptions.firstOrNull {
                        it.profileId == profileId && it.serviceId == identity.serviceId &&
                            it.url == identity.url && it.uid > 0
                    }
                    render(viewModel.states.value.orEmpty())
                },
                { error ->
                    if (destroyed || channel != identity || !isCurrentProfile()) return@subscribe
                    subscription = null
                    updateMenu()
                    Log.e(TAG, "Could not observe the channel subscription", error)
                }
            )
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_channel_feed_refresh, menu)
        menuItem = menu.findItem(R.id.menu_item_refresh_channel_feed)
        updateMenu()
    }

    override fun onPrepareMenu(menu: Menu) = updateMenu()

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.menu_item_refresh_channel_feed) return false
        refresh()
        return true
    }

    private fun refresh() {
        val current = subscription ?: return
        if (!isAvailable() || !canShowFeedback()) return
        if (currentState(viewModel.states.value.orEmpty()) is ChannelFeedRefreshState.Running) return
        viewModel.refresh(ChannelFeedRefreshRequest.from(current))
    }

    private fun updateMenu() {
        val running = currentState(viewModel.states.value.orEmpty()) is ChannelFeedRefreshState.Running
        menuItem?.apply {
            isVisible = isAvailable()
            isEnabled = !running
            setTitle(if (running) R.string.channel_feed_refresh_running else R.string.channel_feed_refresh_title)
        }
    }

    private fun render(states: Map<ChannelFeedRefreshKey, ChannelFeedRefreshState>) {
        updateMenu()
        if (destroyed || !isCurrentProfile() || !canShowFeedback()) {
            dismissFeedback()
            return
        }
        val state = currentState(states)
        if (state is ChannelFeedRefreshState.Running) {
            runningRequest = state.request
            if (progressSnackbar?.isShownOrQueued != true) {
                resultSnackbar?.dismiss()
                resultSnackbar = null
                fragment.view?.let { view ->
                    progressSnackbar = Snackbar.make(view, R.string.channel_feed_refresh_running, Snackbar.LENGTH_INDEFINITE)
                        .also(Snackbar::show)
                }
            }
            return
        }
        progressSnackbar?.dismiss()
        progressSnackbar = null
        if (state !is ChannelFeedRefreshState.Finished) return

        val message = when (state.outcome) {
            ChannelFeedRefreshState.Outcome.SUCCESS -> R.string.channel_feed_refresh_complete
            ChannelFeedRefreshState.Outcome.PARTIAL -> R.string.channel_feed_refresh_partial
            ChannelFeedRefreshState.Outcome.FAILED -> R.string.channel_feed_refresh_failed
            ChannelFeedRefreshState.Outcome.UNAVAILABLE -> R.string.channel_feed_refresh_unavailable
        }
        fragment.view?.let { view ->
            resultSnackbar?.dismiss()
            resultSnackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG).apply {
                if (state.outcome != ChannelFeedRefreshState.Outcome.SUCCESS && isAvailable()) {
                    setAction(R.string.retry) { refresh() }
                }
                show()
            }
            runningRequest = null
            viewModel.consume(state)
        }
    }

    private fun currentState(states: Map<ChannelFeedRefreshKey, ChannelFeedRefreshState>): ChannelFeedRefreshState? {
        val identity = channel ?: return null
        val subscriptionId = subscription?.uid ?: runningRequest?.subscriptionId ?: return null
        return states[ChannelFeedRefreshKey(profileId, subscriptionId)]?.takeIf {
            it.request.serviceId == identity.serviceId && it.request.url == identity.url
        }
    }

    private fun isAvailable(): Boolean {
        val identity = channel ?: return false
        return !destroyed && isCurrentProfile() && subscription != null &&
            KidsMode.isSubscriptionAllowed(context, identity.serviceId)
    }

    private fun isCurrentProfile(): Boolean = profileId == ProfileManager.getActiveProfileId(context)

    private fun canShowFeedback(): Boolean {
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        // The player is a sibling sheet; expanding it does not pause the channel fragment.
        val playerCoversChannel = playerBehavior != null && playerSheet?.isShown == true &&
            playerSheet.childCount > 0 && playerState != BottomSheetBehavior.STATE_HIDDEN &&
            playerState != BottomSheetBehavior.STATE_COLLAPSED
        return !playerCoversChannel
    }

    private fun clearStaleProfile() {
        // A restored channel page can briefly outlive the profile that created it.
        if (destroyed || isCurrentProfile()) return
        subscriptionDisposable?.dispose()
        subscription = null
        runningRequest = null
        dismissFeedback()
        updateMenu()
    }

    private fun dismissFeedback() {
        progressSnackbar?.dismiss()
        progressSnackbar = null
        resultSnackbar?.dismiss()
        resultSnackbar = null
    }

    private data class ChannelIdentity(val serviceId: Int, val url: String)

    companion object {
        private const val TAG = "ChannelFeedRefreshMenu"

        @JvmStatic
        fun attach(fragment: Fragment): ChannelFeedRefreshMenu = ChannelFeedRefreshMenu(fragment)
    }
}
