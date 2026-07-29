package org.schabi.newpipe.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.database.feed.dao.FeedDAO
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamType

class FeedDAOTest {
    private lateinit var db: AppDatabase
    private lateinit var feedDAO: FeedDAO
    private lateinit var streamDAO: StreamDAO
    private lateinit var subscriptionDAO: SubscriptionDAO

    private val serviceId = ServiceList.YouTube.serviceId

    private val stream1 = StreamEntity(1, serviceId, "https://youtube.com/watch?v=1", "stream 1", StreamType.VIDEO_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-01", OffsetDateTime.parse("2023-01-01T00:00:00Z"))
    private val stream2 = StreamEntity(2, serviceId, "https://youtube.com/watch?v=2", "stream 2", StreamType.VIDEO_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-02", OffsetDateTime.parse("2023-01-02T00:00:00Z"))
    private val stream3 = StreamEntity(3, serviceId, "https://youtube.com/watch?v=3", "stream 3", StreamType.LIVE_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-03", OffsetDateTime.parse("2023-01-03T00:00:00Z"))
    private val stream4 = StreamEntity(4, serviceId, "https://youtube.com/watch?v=4", "stream 4", StreamType.VIDEO_STREAM, 1000, "channel-2", "https://youtube.com/channel/2", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-10", OffsetDateTime.parse("2023-08-10T00:00:00Z"))
    private val stream5 = StreamEntity(5, serviceId, "https://youtube.com/watch?v=5", "stream 5", StreamType.VIDEO_STREAM, 1000, "channel-2", "https://youtube.com/channel/2", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-20", OffsetDateTime.parse("2023-08-20T00:00:00Z"))
    private val stream6 = StreamEntity(6, serviceId, "https://youtube.com/watch?v=6", "stream 6", StreamType.VIDEO_STREAM, 1000, "channel-3", "https://youtube.com/channel/3", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-09-01", OffsetDateTime.parse("2023-09-01T00:00:00Z"))
    private val stream7 = StreamEntity(7, serviceId, "https://youtube.com/watch?v=7", "stream 7", StreamType.VIDEO_STREAM, 1000, "channel-4", "https://youtube.com/channel/4", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-10", OffsetDateTime.parse("2023-08-10T00:00:00Z"))

    private val allStreams = listOf(
        stream1,
        stream2,
        stream3,
        stream4,
        stream5,
        stream6,
        stream7
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        feedDAO = db.feedDAO()
        streamDAO = db.streamDAO()
        subscriptionDAO = db.subscriptionDAO()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepOne() {
        setupUnlinkDelete("2023-08-15T00:00:00Z")
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepMultiple() {
        setupUnlinkDelete("2023-08-01T00:00:00Z")
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream4, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    @Test
    fun youtubeMusicFeedOnlyIncludesMusicMemberships() {
        clearAndFillTables()
        val musicChannel = subscriptionDAO.getSubscriptionDirect(
            serviceId,
            "https://youtube.com/channel/1"
        )!!
        musicChannel.youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
        subscriptionDAO.update(musicChannel)
        feedDAO.deleteAll()
        feedDAO.insertAll(
            listOf(
                FeedEntity(1, 1, SubscriptionEntity.YOUTUBE_MODE_MUSIC),
                FeedEntity(2, 1, SubscriptionEntity.YOUTUBE_MODE_MUSIC),
                FeedEntity(3, 1, SubscriptionEntity.YOUTUBE_MODE_MUSIC)
            )
        )

        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
        ).blockingGet()

        assertEqual(streams, listOf(stream1, stream2, stream3))
    }

    @Test
    fun youtubeAndYoutubeMusicHaveIndependentRefreshTimes() {
        clearAndFillTables()
        val sharedChannel = subscriptionDAO.getSubscriptionDirect(
            serviceId,
            "https://youtube.com/channel/1"
        )!!
        sharedChannel.youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_ALL
        subscriptionDAO.update(sharedChannel)
        val regularUpdate = OffsetDateTime.parse("2023-09-01T00:00:00Z")
        val musicUpdate = OffsetDateTime.parse("2023-09-02T00:00:00Z")
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(
                sharedChannel.uid,
                SubscriptionEntity.YOUTUBE_MODE_REGULAR,
                regularUpdate
            )
        )
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(
                sharedChannel.uid,
                SubscriptionEntity.YOUTUBE_MODE_MUSIC,
                musicUpdate
            )
        )

        assertEquals(
            regularUpdate,
            feedDAO.oldestSubscriptionUpdateFromAll(
                serviceId,
                SubscriptionEntity.YOUTUBE_MODE_REGULAR
            ).blockingFirst().first()
        )
        assertEquals(
            musicUpdate,
            feedDAO.oldestSubscriptionUpdateFromAll(
                serviceId,
                SubscriptionEntity.YOUTUBE_MODE_MUSIC
            ).blockingFirst().first()
        )
    }

    @Test
    fun feedOnlyIncludesSelectedService() {
        db.clearAllTables()
        val otherServiceId = ServiceList.SoundCloud.serviceId
        val youtubeSubscription = SubscriptionEntity(
            uid = 1,
            serviceId = serviceId,
            url = "https://youtube.com/channel/1",
            name = "YouTube channel"
        )
        val soundCloudSubscription = SubscriptionEntity(
            uid = 2,
            serviceId = otherServiceId,
            url = "https://soundcloud.com/channel-2",
            name = "SoundCloud channel"
        )
        val soundCloudStream = StreamEntity(
            8,
            otherServiceId,
            "https://soundcloud.com/channel-2/stream",
            "SoundCloud stream",
            StreamType.AUDIO_STREAM,
            1000,
            "SoundCloud channel",
            "https://soundcloud.com/channel-2",
            "",
            100,
            "2023-09-02",
            OffsetDateTime.parse("2023-09-02T00:00:00Z")
        )
        subscriptionDAO.insertAll(listOf(youtubeSubscription, soundCloudSubscription))
        streamDAO.insertAll(listOf(stream1, soundCloudStream))
        feedDAO.insertAll(listOf(FeedEntity(1, 1), FeedEntity(8, 2)))

        val youtubeStreams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        ).blockingGet()
        val soundCloudStreams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = otherServiceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        ).blockingGet()

        assertEqual(youtubeStreams, listOf(stream1))
        assertEqual(soundCloudStreams, listOf(soundCloudStream))
    }

    private fun assertEqual(streams: List<StreamWithState>?, allowedStreams: List<StreamEntity>) {
        assertNotNull(streams)
        assertEquals(
            allowedStreams,
            streams!!
                .map { it.stream }
                .sortedBy { it.uid }
                .toList()
        )
    }

    private fun setupUnlinkDelete(time: String) {
        clearAndFillTables()
        Single.fromCallable {
            feedDAO.unlinkStreamsOlderThan(OffsetDateTime.parse(time))
        }.blockingSubscribe()
        Single.fromCallable {
            streamDAO.deleteOrphans()
        }.blockingSubscribe()
    }

    private fun clearAndFillTables() {
        db.clearAllTables()
        streamDAO.insertAll(allStreams)
        subscriptionDAO.insertAll(
            listOf(
                SubscriptionEntity.from(channelInfo("1", "https://youtube.com/channel/1", "channel-1")),
                SubscriptionEntity.from(channelInfo("2", "https://youtube.com/channel/2", "channel-2")),
                SubscriptionEntity.from(channelInfo("3", "https://youtube.com/channel/3", "channel-3")),
                SubscriptionEntity.from(channelInfo("4", "https://youtube.com/channel/4", "channel-4"))
            )
        )
        feedDAO.insertAll(
            listOf(
                FeedEntity(1, 1),
                FeedEntity(2, 1),
                FeedEntity(3, 1),
                FeedEntity(4, 2),
                FeedEntity(5, 2),
                FeedEntity(6, 3),
                FeedEntity(7, 4)
            )
        )
    }

    private fun channelInfo(id: String, url: String, name: String): ChannelInfo {
        val channelInfoConstructor = ChannelInfo::class.java.constructors
            .first { it.parameterTypes.size == 6 || it.parameterTypes.size == 5 }
        val constructorArguments: Array<Any> = if (channelInfoConstructor.parameterTypes.size == 6) {
            arrayOf<Any>(serviceId, id, url, url, name, createListLinkHandler(id, url))
        } else {
            arrayOf<Any>(serviceId, id, url, url, name)
        }
        return channelInfoConstructor.newInstance(*constructorArguments) as ChannelInfo
    }

    private fun createListLinkHandler(id: String, url: String): ListLinkHandler {
        val constructor = ListLinkHandler::class.java.constructors
            .first { it.parameterTypes.size == 5 }
        val contentFilters: List<Any> = emptyList()
        val sortFilter: Any = if (List::class.java.isAssignableFrom(constructor.parameterTypes[4])) {
            emptyList<Any>()
        } else {
            ""
        }
        return constructor.newInstance(url, url, id, contentFilters, sortFilter)
            as ListLinkHandler
    }
}
