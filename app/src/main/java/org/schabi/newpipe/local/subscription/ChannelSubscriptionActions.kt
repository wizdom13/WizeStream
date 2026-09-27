package org.schabi.newpipe.local.subscription

import android.content.Context
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.profiles.ProfileRecord
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

object ChannelSubscriptionActions {
    @JvmStatic
    fun otherProfiles(context: Context): List<ProfileRecord> {
        val activeProfileId = ProfileManager.getActiveProfileId(context)
        return ProfileManager.getProfiles(context).filter { it.id != activeProfileId }
    }

    @JvmStatic
    fun subscribe(context: Context, item: ChannelInfoItem): Single<SubscriptionEntity> = subscribeToProfile(
        context,
        item,
        ProfileManager.getActiveProfile(context)
    )

    @JvmStatic
    fun subscribeToProfile(
        context: Context,
        item: ChannelInfoItem,
        profile: ProfileRecord
    ): Single<SubscriptionEntity> {
        val entity = SubscriptionEntity(
            serviceId = item.serviceId,
            url = item.url,
            name = item.name,
            avatarUrl = ImageStrategy.imageListToDbUrl(
                ExtractorImageCompat.thumbnailImages(item)
            ),
            subscriberCount = item.subscriberCount.takeIf { it >= 0 },
            description = item.description
        )
        return Single.fromCallable {
            SubscriptionManager(context.applicationContext, profile.id)
                .insertSubscriptionAndReturn(entity)
        }
    }
}
