package org.schabi.newpipe.database.subscription

/** Minimal JVM representation required by the platform-neutral synchronization model. */
data class SubscriptionEntity(
    var uid: Long = 0,
    var serviceId: Int = -1,
    var url: String? = null,
    var name: String? = null,
    var avatarUrl: String? = null,
    var subscriberCount: Long? = null,
    var description: String? = null,
    var notificationMode: Int = 0,
    var youtubeModeMask: Int = YOUTUBE_MODE_REGULAR
) {
    companion object {
        const val YOUTUBE_MODE_REGULAR = 1
        const val YOUTUBE_MODE_MUSIC = 2
        const val YOUTUBE_MODE_ALL = YOUTUBE_MODE_REGULAR or YOUTUBE_MODE_MUSIC
    }
}
