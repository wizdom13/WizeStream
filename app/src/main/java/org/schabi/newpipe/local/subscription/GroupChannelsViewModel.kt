package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.profiles.ProfileManager

class GroupChannelsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = NewPipeDatabase.getInstance(application)
    private val subscriptionManager = SubscriptionManager(application)
    private val profileId = ProfileManager.getActiveProfileId(application)
    private val mutableChannels = MutableLiveData<List<ChannelInfoItem>>()
    val channels: LiveData<List<ChannelInfoItem>> = mutableChannels
    private var disposable: Disposable? = null

    fun load(groupId: Long) {
        disposable?.dispose()
        val source = if (groupId == FeedGroupEntity.GROUP_ALL_ID) {
            subscriptionManager.subscriptions()
        } else {
            database.feedGroupDAO()
                .getSubscriptionIdsForProfile(profileId, groupId)
                .switchMap { ids ->
                    subscriptionManager.subscriptions().map { subscriptions ->
                        subscriptions.filter { it.uid in ids }
                    }
                }
        }
        disposable = source
            .subscribeOn(Schedulers.io())
            .map { subscriptions -> subscriptions.map { it.toChannelInfoItem() } }
            .subscribe(mutableChannels::postValue)
    }

    override fun onCleared() {
        disposable?.dispose()
    }
}
