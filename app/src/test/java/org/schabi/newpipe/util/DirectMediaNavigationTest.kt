/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.app.Application
import android.content.Intent
import android.os.Parcel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class DirectMediaNavigationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val station = PlayQueueItem.radioStation(
        "Station",
        "https://radio.example/live?token=current",
        "Country · Language",
        true,
        "https://radio.example/logo.png"
    )

    @After
    fun tearDown() {
        SerializedCache.getInstance().clear()
    }

    @Test
    fun detailIntentKeepsStationMetadataWhenTheSerializedQueueExpires() {
        NavigationHelper.openVideoDetail(context, station.serviceId, station.url, station.title, queue(station), false)
        val intent = roundTrip(requireNotNull(shadowOf(context).nextStartedActivity))
        assertNotNull(intent.getStringExtra(Player.PLAY_QUEUE_KEY))
        SerializedCache.getInstance().clear()

        val restored = requireNotNull(DirectMediaNavigation.restoreFromIntent(intent, null)).item!!
        assertItem(station, restored)
        // The URL has no manifest extension: this must retain the explicit HLS flag.
        assertEquals("application/x-mpegURL", restored.mimeType)
    }

    @Test
    fun aMatchingCachedQueueRetainsItsOtherStationsAndSelection() {
        val other = PlayQueueItem.radioStation("Other", "https://other.example/live", null, false, null)
        val cached = LocalMediaPlayQueue(listOf(other, station), 1)
        val intent = intent(station)
        DirectMediaNavigation.saveToIntent(intent, cached)

        assertSame(cached, DirectMediaNavigation.restoreFromIntent(roundTrip(intent), cached))
        assertEquals(1, cached.index)
        assertEquals(2, cached.size())
    }

    @Test
    fun staleIntentAndActiveQueueCannotSupplyAnotherStationsMetadata() {
        val intent = intent(station)
        DirectMediaNavigation.saveToIntent(intent, queue(station))
        val otherUrl = "https://other.example/audio"
        intent.putExtra(KEY_URL, otherUrl).putExtra(KEY_TITLE, "Other")

        val restored = DirectMediaNavigation.restoreFromIntent(roundTrip(intent), queue(station))!!.item!!
        assertEquals(otherUrl, restored.url)
        assertEquals("Other", restored.title)
        assertNull(restored.mimeType)
        assertNull(restored.localThumbnailUrl)
        val fromActive = DirectMediaNavigation.resolveQueue(station.serviceId, otherUrl, "Other", null, station, queue(station))!!.item!!
        assertEquals(otherUrl, fromActive.url)
        assertNull(fromActive.localThumbnailUrl)

        // Reusing an intent for another service must not revive a radio descriptor.
        intent.putExtra(KEY_SERVICE_ID, 0)
        assertNull(DirectMediaNavigation.restoreFromIntent(intent, null))
        assertNull(DirectMediaNavigation.restoreFromIntent(intent, queue(station)))
        assertNull(DirectMediaNavigation.resolveQueue(PlayQueueItem.LIVE_TV_SERVICE_ID, station.url, "TV", null, station, queue(station)))
    }

    @Test
    fun aMatchingSavedItemOrActiveQueueRetainsDirectSourceMetadata() {
        val active = queue(station)
        assertSame(active, DirectMediaNavigation.resolveQueue(station.serviceId, station.url, station.title, null, null, active))
        val saved = PlayQueueItem.radioStation("Saved station", station.url, "Saved details", true, "https://radio.example/saved.png")
        val restored = DirectMediaNavigation.resolveQueue(saved.serviceId, saved.url, saved.title, null, saved, active)!!.item!!
        assertItem(saved, restored)
    }

    @Test
    fun aMixedQueueStillWorksWhenItsSelectedItemIsRemote() {
        val video = PlayQueueItem(StreamInfoItem(0, "https://video.example/watch", "Video", StreamType.VIDEO_STREAM))
        val mixed = LocalMediaPlayQueue(listOf(station, video), 1)
        // Remote queue URLs can differ from the requested URL because of timestamps.
        val intent = NavigationHelper.getStreamIntent(context, 0, video.url + "?t=42", video.title)
        assertSame(mixed, DirectMediaNavigation.restoreFromIntent(intent, mixed))
        assertSame(mixed, DirectMediaNavigation.resolveQueue(0, intent.getStringExtra(KEY_URL), video.title, mixed, station, null))
    }

    @Test
    fun localAndLiveTvIntentFallbacksKeepTheirOriginalSourceTypes() {
        val local = PlayQueueItem.localMedia(
            "Local video", "content://media/video/42", 120, "Artist", "Album", "Folder", "video/mp4", 42, true, "content://media/art/42"
        )
        val tv = PlayQueueItem.liveTv("TV", "https://tv.example/live", "Group", "application/x-mpegURL", "https://tv.example/logo.png")
        for (item in listOf(local, tv)) {
            val intent = intent(item)
            DirectMediaNavigation.saveToIntent(intent, queue(item))
            assertItem(item, DirectMediaNavigation.restoreFromIntent(roundTrip(intent), null)!!.item!!)
        }
    }

    @Test
    fun legacyRadioUrlsInferHlsFromThePathAndRejectInvalidUrls() {
        val manifestUrl = "https://radio.example/stream.M3U8?token=1"
        val manifest = DirectMediaNavigation.resolveQueue(-2, manifestUrl, "Legacy", null, null, null)!!.item!!
        assertTrue(manifest.isRadio)
        assertEquals(manifestUrl, manifest.url)
        assertEquals("application/x-mpegURL", manifest.mimeType)

        val progressive = DirectMediaNavigation.resolveQueue(-2, "https://radio.example/live?format=m3u8", "Legacy", null, null, null)!!.item!!
        assertNull(progressive.mimeType)
        assertFalse(progressive.isLocalMedia)
        for (url in listOf("", "http://radio.example/live", "https://user:password@radio.example/live", "not a URL")) {
            assertNull(DirectMediaNavigation.resolveQueue(-2, url, "Invalid", null, null, null))
        }
        assertNull(DirectMediaNavigation.resolveQueue(-99, station.url, "Unknown", null, station, null))
        assertNull(DirectMediaNavigation.resolveQueue(-1, "content://media/audio/1", "Missing local item", null, null, null))
    }

    private fun queue(item: PlayQueueItem): PlayQueue = LocalMediaPlayQueue(listOf(item), 0)

    private fun intent(item: PlayQueueItem): Intent = NavigationHelper.getStreamIntent(context, item.serviceId, item.url, item.title)

    private fun roundTrip(intent: Intent): Intent {
        val parcel = Parcel.obtain()
        return try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel).apply { setExtrasClassLoader(PlayQueueItem::class.java.classLoader) }
        } finally {
            parcel.recycle()
        }
    }

    private fun assertItem(expected: PlayQueueItem, actual: PlayQueueItem) {
        assertTrue(actual.isDirectMedia)
        assertEquals(expected.sourceType, actual.sourceType)
        assertEquals(expected.serviceId, actual.serviceId)
        assertEquals(expected.url, actual.url)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.uploader, actual.uploader)
        assertEquals(expected.streamType, actual.streamType)
        assertEquals(expected.mimeType, actual.mimeType)
        assertEquals(expected.localThumbnailUrl, actual.localThumbnailUrl)
        assertEquals(expected.duration, actual.duration)
        assertEquals(expected.localMediaId, actual.localMediaId)
        assertEquals(expected.album, actual.album)
        assertEquals(expected.folder, actual.folder)
    }
}
