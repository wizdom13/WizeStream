/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.profiles.ProfileIcon
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(AndroidJUnit4::class)
class ProfileSyncIdentityCompatibilityTest {
    private val peerId = DeviceIdentity(generateKeyPair(KeyType.ED25519).first).peerId.toBase58()
    private val json = Json { encodeDefaults = true }

    @Test
    fun defaultProfileCatalogSurvivesWireRoundTrip() {
        val request = ProfileSyncRequest(
            profiles = listOf(
                SyncedProfile(
                    id = ProfileManager.DEFAULT_PROFILE_ID,
                    name = "",
                    iconKey = ProfileIcon.PERSON.key,
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 0
                )
            )
        )
        val decodedRequest = ProfileSyncCodec.decodeRequest(ProfileSyncCodec.encodeRequest(request))
        ProfileSyncValidation.validateRequest(decodedRequest)

        val response = ProfileSyncResponse(accepted = true, profiles = decodedRequest.profiles)
        val decodedResponse =
            ProfileSyncCodec.decodeResponse(ProfileSyncCodec.encodeResponse(response))
        ProfileSyncValidation.validateResponse(decodedResponse)
        assertEquals(ProfileManager.DEFAULT_PROFILE_ID, decodedResponse.profiles.single().id)
    }

    @Test
    fun subscriptionProfilesSurviveRequestAndResponseRoundTrips() {
        validProfileIds().forEach { profileId ->
            val changes = listOf(subscription(profileId))
            SubscriptionSyncValidation.validateRequest(
                roundTrip(
                    SubscriptionSyncRequest(
                        knownRevisions = emptyMap(),
                        changes = changes,
                        hasMore = false
                    )
                )
            )
            SubscriptionSyncValidation.validateResponse(
                roundTrip(SubscriptionSyncResponse(accepted = true, changes = changes))
            )
        }
    }

    @Test
    fun watchHistoryProfilesSurviveRequestAndResponseRoundTrips() {
        validProfileIds().forEach { profileId ->
            val changes = listOf(history(profileId))
            HistorySyncValidation.validateRequest(
                roundTrip(
                    HistorySyncRequest(
                        category = HistorySyncCategory.WATCH,
                        knownRevisions = emptyMap(),
                        changes = changes,
                        hasMore = false
                    )
                )
            )
            HistorySyncValidation.validateResponse(
                HistorySyncCategory.WATCH,
                roundTrip(
                    HistorySyncResponse(
                        accepted = true,
                        category = HistorySyncCategory.WATCH,
                        changes = changes
                    )
                )
            )
        }
    }

    @Test
    fun playlistProfilesSurviveRequestAndResponseRoundTrips() {
        validProfileIds().forEach { profileId ->
            val changes = listOf(playlist(profileId))
            PlaylistSyncValidation.validateRequest(
                roundTrip(
                    PlaylistSyncRequest(
                        knownRevisions = emptyMap(),
                        changes = changes,
                        hasMore = false
                    )
                )
            )
            PlaylistSyncValidation.validateResponse(
                roundTrip(PlaylistSyncResponse(accepted = true, changes = changes))
            )
        }
    }

    @Test
    fun feedGroupProfilesSurviveRequestAndResponseRoundTrips() {
        validProfileIds().forEach { profileId ->
            val changes = listOf(feedGroupOrder(profileId))
            StructuredPreferenceSyncValidation.validateRequest(
                roundTrip(
                    StructuredPreferenceSyncRequest(
                        category = StructuredPreferenceCategory.FEED_GROUPS,
                        knownRevisions = emptyMap(),
                        changes = changes,
                        hasMore = false
                    )
                )
            )
            StructuredPreferenceSyncValidation.validateResponse(
                StructuredPreferenceCategory.FEED_GROUPS,
                roundTrip(
                    StructuredPreferenceSyncResponse(
                        accepted = true,
                        category = StructuredPreferenceCategory.FEED_GROUPS,
                        changes = changes
                    )
                )
            )
        }
    }

    @Test
    fun malformedProfileIdsRemainRejectedAcrossCategories() {
        listOf(
            "",
            "0-0-0-0-0",
            "00000000-0000-0000-0000-00000000000",
            "ABCDEFAB-1234-5678-9ABC-DEF012345678",
            "zzzzzzzz-1234-5678-9abc-def012345678",
            " ${ProfileManager.DEFAULT_PROFILE_ID}"
        ).forEach { profileId ->
            assertThrows(SubscriptionSyncException::class.java) {
                SubscriptionSyncValidation.validateChanges(listOf(subscription(profileId)))
            }
            assertThrows(HistorySyncException::class.java) {
                HistorySyncValidation.validateChanges(
                    HistorySyncCategory.WATCH,
                    listOf(history(profileId))
                )
            }
            assertThrows(PlaylistSyncException::class.java) {
                PlaylistSyncValidation.validateChanges(listOf(playlist(profileId)))
            }
            assertThrows(StructuredPreferenceSyncException::class.java) {
                StructuredPreferenceSyncValidation.validateChanges(
                    StructuredPreferenceCategory.FEED_GROUPS,
                    listOf(feedGroupOrder(profileId))
                )
            }
        }
    }

    private fun validProfileIds(): List<String> = listOf(
        ProfileManager.DEFAULT_PROFILE_ID,
        "00000001-0000-0000-0000-000000000001",
        UUID.randomUUID().toString()
    )

    private inline fun <reified T> roundTrip(value: T): T = json.decodeFromString(json.encodeToString(value))

    private fun subscription(profileId: String): SubscriptionChange {
        val subscription = SyncedSubscription(serviceId = 0, url = STREAM_URL, name = "Imported channel")
        return SubscriptionChange(
            originPeerId = peerId,
            originRevision = 1,
            lamportVersion = 1,
            recordId = SubscriptionRecordId.from(profileId, subscription.serviceId, subscription.url),
            profileId = profileId,
            serviceId = subscription.serviceId,
            url = subscription.url,
            type = SubscriptionChangeType.UPSERT,
            subscription = subscription
        )
    }

    private fun history(profileId: String): HistoryChange {
        val stream = SyncedHistoryStream(
            serviceId = 0,
            url = STREAM_URL,
            title = "Imported video",
            streamType = "VIDEO_STREAM",
            duration = 120,
            uploader = "Channel"
        )
        return HistoryChange(
            category = HistorySyncCategory.WATCH,
            originPeerId = peerId,
            originRevision = 1,
            lamportVersion = 1,
            recordId = HistoryRecordId.progress(profileId, stream.identity),
            profileId = profileId,
            recordType = HistoryRecordType.PLAYBACK_PROGRESS,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(playbackProgress = SyncedPlaybackProgress(stream, 30_000, 1_000))
        )
    }

    private fun playlist(profileId: String): PlaylistChange = PlaylistChange(
        originPeerId = peerId,
        originRevision = 1,
        lamportVersion = 1,
        recordId = UUID.randomUUID().toString(),
        profileId = profileId,
        recordType = PlaylistRecordType.LOCAL_PLAYLIST,
        type = PlaylistChangeType.UPSERT,
        record = SyncedPlaylistRecord(
            localPlaylist = SyncedLocalPlaylist(
                name = "Imported playlist",
                isThumbnailPermanent = false,
                displayIndex = 0
            )
        )
    )

    private fun feedGroupOrder(profileId: String): StructuredPreferenceChange = StructuredPreferenceChange(
        category = StructuredPreferenceCategory.FEED_GROUPS,
        originPeerId = peerId,
        originRevision = 1,
        lamportVersion = 1,
        recordId = StructuredPreferenceRecordId.feedGroupOrder(profileId),
        recordType = StructuredPreferenceRecordType.FEED_GROUP_ORDER,
        type = StructuredPreferenceChangeType.UPSERT,
        record = SyncedStructuredPreferenceRecord(feedGroupOrder = SyncedFeedGroupOrder(emptyList(), profileId))
    )

    private companion object {
        const val STREAM_URL = "https://www.youtube.com/watch?v=BaW_jenozKc"
    }
}
