package org.schabi.newpipe.local.subscription

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import org.schabi.newpipe.util.ThemeHelper.getItemViewMode

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var activeProfileId: String? = null
    private var feedGroupItemsDisposable = Disposable.empty()
    private var stateItemsDisposable = Disposable.empty()

    // true -> list view, false -> grid view
    private val listViewMode = BehaviorProcessor.createDefault(
        !shouldUseGridForSubscription(application)
    )
    private val listViewModeFlowable = listViewMode.distinctUntilChanged()

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    private val mutableFeedGroupsLiveData = MutableLiveData<Pair<List<Group>, Boolean>>()
    private val filterQuery = BehaviorProcessor.createDefault("")
    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData
    val feedGroupsLiveData: LiveData<Pair<List<Group>, Boolean>> = mutableFeedGroupsLiveData

    init {
        setProfile(ProfileManager.getActiveProfileId(application))
    }

    @MainThread
    fun setProfile(profileId: String) {
        if (activeProfileId == profileId) return

        stateItemsDisposable.dispose()
        feedGroupItemsDisposable.dispose()
        activeProfileId = profileId

        // The ViewModel survives activity recreation when switching profiles. Drop its cached
        // rows before observing the new profile, including when that profile has no subscriptions.
        mutableStateLiveData.value = SubscriptionState.LoadedState(emptyList())
        mutableFeedGroupsLiveData.value = Pair(emptyList(), getListViewMode())
        feedGroupItemsDisposable = observeFeedGroups(profileId)
        stateItemsDisposable = observeSubscriptions(profileId)
    }

    private fun observeFeedGroups(profileId: String): Disposable = Flowable
        .combineLatest(
            FeedDatabaseManager(getApplication(), profileId).groups(),
            listViewModeFlowable,
            filterQuery.distinctUntilChanged()
        ) { groups, listView, query ->
            Triple(groups, listView, query)
        }
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .map { (feedGroups, listViewMode, query) ->
            val filteredGroups = if (ContextualSearchHelper.isActive(query)) {
                feedGroups.filter { group ->
                    ContextualSearchHelper.matches(query, group.name)
                }
            } else {
                feedGroups
            }
            Pair(
                filteredGroups.map(
                    if (listViewMode) ::FeedGroupCardItem else ::FeedGroupCardGridItem
                ),
                listViewMode
            )
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            { mutableFeedGroupsLiveData.value = it },
            { mutableStateLiveData.value = SubscriptionState.ErrorState(it) }
        )

    private fun observeSubscriptions(profileId: String): Disposable {
        val subscriptionManager = SubscriptionManager(getApplication(), profileId)
        return Flowable.combineLatest(
            FeedScope.changes(getApplication()),
            filterQuery.distinctUntilChanged(),
            ::Pair
        )
            .switchMap { (feedScope, query) ->
                subscriptionManager.getSubscriptionsForScope(
                    scope = feedScope,
                    filterQuery = query
                )
                    .subscribeOn(Schedulers.io())
                    .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
            }
            .map { it.map { entity -> ChannelItem(entity.toChannelInfoItem(), entity.uid, ChannelItem.ItemVersion.MINI) } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { mutableStateLiveData.value = SubscriptionState.LoadedState(it) },
                { mutableStateLiveData.value = SubscriptionState.ErrorState(it) }
            )
    }

    override fun onCleared() {
        super.onCleared()
        stateItemsDisposable.dispose()
        feedGroupItemsDisposable.dispose()
    }

    fun setListViewMode(newListViewMode: Boolean) {
        listViewMode.onNext(newListViewMode)
    }

    fun getListViewMode(): Boolean {
        return listViewMode.value ?: true
    }

    fun setFilterQuery(query: String) {
        filterQuery.onNext(ContextualSearchHelper.normalizeQuery(query))
    }

    sealed class SubscriptionState {
        data class LoadedState(val subscriptions: List<Group>) : SubscriptionState()
        data class ErrorState(val error: Throwable? = null) : SubscriptionState()
    }

    companion object {

        /**
         * Returns whether to use GridLayout mode for Subscription Fragment.
         *
         * ### Current mapping:
         *
         *  | ItemViewMode | ItemVersion | Span count |
         *  |---|---|---|
         *  | AUTO | MINI | 1 |
         *  | LIST | MINI | 1 |
         *  | CARD | GRID | > 1 (ThemeHelper defined) |
         *  | GRID | GRID | > 1 (ThemeHelper defined) |
         *
         *  @see [SubscriptionViewModel.shouldUseGridForSubscription] to modify Layout Manager
         */
        fun shouldUseGridForSubscription(context: Context): Boolean {
            val itemViewMode = getItemViewMode(context)
            return itemViewMode == ItemViewMode.GRID || itemViewMode == ItemViewMode.CARD
        }
    }
}
