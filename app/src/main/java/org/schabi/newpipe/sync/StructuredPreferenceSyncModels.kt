/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable

internal const val STRUCTURED_PREFERENCE_SYNC_PROTOCOL_ID =
    "/wizestream/structured-preferences/1.0.0"
internal const val STRUCTURED_PREFERENCE_SYNC_VERSION = 1
internal const val MAX_STRUCTURED_PREFERENCE_CHANGES_PER_BATCH = 8
internal const val MAX_FEED_GROUPS = 256
internal const val MAX_HOME_TABS = 64
internal const val MAX_STRUCTURED_NAME_LENGTH = 512
internal const val MAX_STRUCTURED_URL_LENGTH = 4_096
internal const val MAX_FILTER_VALUES = 64
internal const val MAX_FILTER_VALUE_LENGTH = 128
internal const val MAX_PORTABLE_SETTING_VALUE_LENGTH = 1_024
internal const val MAX_DOWNLOAD_DISPLAY_NAME_LENGTH = 512
internal const val MAX_DOWNLOAD_MIME_TYPE_LENGTH = 255
internal const val CHANNEL_PROFILE_PREFIX = "channel_playback_profile.v1."

@Serializable
enum class StructuredPreferenceCategory {
    FEED_GROUPS,
    HOME_TABS,
    CHANNEL_PROFILES,
    FILTERS,
    SETTINGS,
    COMPLETED_DOWNLOADS
}

@Serializable
internal enum class StructuredPreferenceRecordType {
    FEED_GROUP,
    FEED_GROUP_MEMBERSHIP,
    FEED_GROUP_ORDER,
    HOME_TAB,
    HOME_TAB_ORDER,
    CHANNEL_PROFILE_FIELD,
    FILTER_SET,
    PORTABLE_SETTING,
    COMPLETED_DOWNLOAD
}

@Serializable
internal enum class StructuredPreferenceChangeType {
    UPSERT,
    DELETE
}

@Serializable
internal data class SyncedFeedGroup(
    val name: String,
    val iconId: Int
)

@Serializable
internal data class SyncedFeedGroupMembership(
    val groupRecordId: String,
    val serviceId: Int,
    val subscriptionUrl: String
)

@Serializable
internal data class SyncedFeedGroupOrder(
    val groupRecordIds: List<String>
)

@Serializable
internal enum class SyncedHomeTabType {
    BLANK,
    DEFAULT_KIOSK,
    SUBSCRIPTIONS,
    FEED,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    KIOSK,
    CHANNEL,
    LOCAL_PLAYLIST,
    REMOTE_PLAYLIST,
    FEED_GROUP
}

@Serializable
internal data class SyncedHomeTab(
    val type: SyncedHomeTabType,
    val serviceId: Int? = null,
    val url: String? = null,
    val name: String? = null,
    val linkedRecordId: String? = null,
    val kioskId: String? = null
)

@Serializable
internal data class SyncedHomeTabOrder(
    val tabRecordIds: List<String>
)

@Serializable
internal enum class ChannelProfileField {
    SPEED,
    QUALITY,
    CAPTION
}

@Serializable
internal data class SyncedChannelProfileField(
    val profileKey: String,
    val field: ChannelProfileField,
    val speed: Float? = null,
    val textValue: String? = null
)

@Serializable
internal enum class StructuredFilterId {
    CHANNEL_TABS,
    FEED_CHANNEL_TABS,
    SEARCH_SUGGESTIONS
}

@Serializable
internal data class SyncedFilterSet(
    val filterId: StructuredFilterId,
    val values: List<String>
)

internal enum class PortableSettingValueType {
    BOOLEAN,
    STRING,
    FLOAT
}

@Serializable
internal enum class PortableSettingId {
    SERVICE,
    CONTENT_COUNTRY,
    CONTENT_LANGUAGE,
    THEME,
    NIGHT_THEME,
    THEME_COLOR,
    DEFAULT_RESOLUTION,
    DEFAULT_POPUP_RESOLUTION,
    SHOW_HIGHER_RESOLUTIONS,
    DEFAULT_VIDEO_FORMAT,
    DEFAULT_AUDIO_FORMAT,
    AUTOPLAY,
    MINIMIZE_ON_EXIT,
    NATIVE_PIP,
    SEEK_DURATION,
    SEEK_PREVIEW_QUALITY,
    PREFER_ORIGINAL_AUDIO,
    PREFER_DESCRIPTIVE_AUDIO,
    SHOW_AGE_RESTRICTED_CONTENT,
    YOUTUBE_RESTRICTED_MODE,
    SHOW_COMMENTS,
    SHOW_DESCRIPTION,
    SHOW_META_INFO,
    SHOW_NEXT_VIDEO,
    SHOW_THUMBNAILS,
    IMAGE_QUALITY,
    LIST_VIEW_MODE,
    PREFERRED_OPEN_ACTION,
    SHOW_HOLD_TO_APPEND,
    SHOW_PLAY_WITH_KODI,
    START_FULLSCREEN,
    AUTO_QUEUE,
    INEXACT_SEEK,
    CLEAR_QUEUE_CONFIRMATION,
    PLAYBACK_SPEED,
    PLAYBACK_PITCH,
    PLAYBACK_SKIP_SILENCE,
    LEARNING_MODE,
    LEARNING_NOTES,
    LEARNING_PLAYLIST_PROGRESS,
    LEARNING_COUNT_BACKGROUND;

    val valueType: PortableSettingValueType
        get() = when (this) {
            SHOW_HIGHER_RESOLUTIONS,
            PREFER_ORIGINAL_AUDIO,
            PREFER_DESCRIPTIVE_AUDIO,
            SHOW_AGE_RESTRICTED_CONTENT,
            YOUTUBE_RESTRICTED_MODE,
            SHOW_COMMENTS,
            SHOW_DESCRIPTION,
            SHOW_META_INFO,
            SHOW_NEXT_VIDEO,
            SHOW_THUMBNAILS,
            SHOW_HOLD_TO_APPEND,
            SHOW_PLAY_WITH_KODI,
            START_FULLSCREEN,
            AUTO_QUEUE,
            INEXACT_SEEK,
            CLEAR_QUEUE_CONFIRMATION,
            PLAYBACK_SKIP_SILENCE,
            LEARNING_MODE,
            LEARNING_NOTES,
            LEARNING_PLAYLIST_PROGRESS,
            LEARNING_COUNT_BACKGROUND -> PortableSettingValueType.BOOLEAN

            PLAYBACK_SPEED,
            PLAYBACK_PITCH -> PortableSettingValueType.FLOAT

            else -> PortableSettingValueType.STRING
        }
}

@Serializable
internal data class SyncedPortableSetting(
    val settingId: PortableSettingId,
    val booleanValue: Boolean? = null,
    val stringValue: String? = null,
    val floatValue: Float? = null
)

@Serializable
internal data class SyncedCompletedDownload(
    val syncId: String,
    val ownerPeerId: String,
    val sourceUrl: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val completedAtEpochMillis: Long,
    val mediaKind: String
)

@Serializable
internal data class SyncedStructuredPreferenceRecord(
    val feedGroup: SyncedFeedGroup? = null,
    val feedGroupMembership: SyncedFeedGroupMembership? = null,
    val feedGroupOrder: SyncedFeedGroupOrder? = null,
    val homeTab: SyncedHomeTab? = null,
    val homeTabOrder: SyncedHomeTabOrder? = null,
    val channelProfileField: SyncedChannelProfileField? = null,
    val filterSet: SyncedFilterSet? = null,
    val portableSetting: SyncedPortableSetting? = null,
    val completedDownload: SyncedCompletedDownload? = null
)

@Serializable
internal data class StructuredPreferenceChange(
    val category: StructuredPreferenceCategory,
    val originPeerId: String,
    val originRevision: Long,
    val lamportVersion: Long,
    val recordId: String,
    val recordType: StructuredPreferenceRecordType,
    val parentRecordId: String? = null,
    val type: StructuredPreferenceChangeType,
    val record: SyncedStructuredPreferenceRecord? = null
) {
    val versionStamp: StructuredPreferenceVersionStamp
        get() = StructuredPreferenceVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )
}

@Serializable
internal data class StructuredPreferenceSyncRequest(
    val version: Int = STRUCTURED_PREFERENCE_SYNC_VERSION,
    val category: StructuredPreferenceCategory,
    val knownRevisions: Map<String, Long>,
    val changes: List<StructuredPreferenceChange>,
    val hasMore: Boolean
)

@Serializable
internal data class StructuredPreferenceSyncResponse(
    val accepted: Boolean,
    val category: StructuredPreferenceCategory,
    val error: String? = null,
    val knownRevisions: Map<String, Long> = emptyMap(),
    val changes: List<StructuredPreferenceChange> = emptyList(),
    val hasMore: Boolean = false
)

data class StructuredPreferenceSyncResult(
    val peer: TrustedPeer,
    val category: StructuredPreferenceCategory,
    val sentChanges: Int,
    val receivedChanges: Int,
    val affectedRecords: Int,
    val rounds: Int
)

internal data class StructuredPreferenceVersionStamp(
    val lamportVersion: Long,
    val originPeerId: String,
    val originRevision: Long
) : Comparable<StructuredPreferenceVersionStamp> {
    override fun compareTo(other: StructuredPreferenceVersionStamp): Int {
        return compareValuesBy(
            this,
            other,
            StructuredPreferenceVersionStamp::lamportVersion,
            StructuredPreferenceVersionStamp::originPeerId,
            StructuredPreferenceVersionStamp::originRevision
        )
    }
}

internal data class StructuredPreferenceChangeBatch(
    val changes: List<StructuredPreferenceChange>,
    val hasMore: Boolean
)

internal data class StructuredPreferenceApplyResult(
    val acceptedChanges: Int,
    val affectedRecords: Int
)

internal object StructuredPreferenceSyncValidation {
    fun validateRequest(request: StructuredPreferenceSyncRequest) {
        if (request.version != STRUCTURED_PREFERENCE_SYNC_VERSION) {
            throw StructuredPreferenceSyncException(
                "Unsupported structured preference synchronization version: " +
                    request.version
            )
        }
        validateKnownRevisions(request.knownRevisions)
        validateChanges(request.category, request.changes)
    }

    fun validateResponse(
        expectedCategory: StructuredPreferenceCategory,
        response: StructuredPreferenceSyncResponse
    ) {
        if (response.category != expectedCategory) {
            throw StructuredPreferenceSyncException(
                "The remote device returned the wrong structured preference category"
            )
        }
        if (!response.accepted) {
            if (response.error.isNullOrBlank()) {
                throw StructuredPreferenceSyncException(
                    "The remote device rejected structured preference synchronization"
                )
            }
            if (response.error.length > MAX_SYNC_ERROR_LENGTH) {
                throw StructuredPreferenceSyncException(
                    "The structured preference synchronization error is too large"
                )
            }
            return
        }
        if (response.error != null) {
            throw StructuredPreferenceSyncException(
                "A successful structured preference response has an error"
            )
        }
        validateKnownRevisions(response.knownRevisions)
        validateChanges(response.category, response.changes)
    }

    fun validateKnownRevisions(knownRevisions: Map<String, Long>) {
        if (knownRevisions.size > MAX_SYNC_ORIGINS) {
            throw StructuredPreferenceSyncException(
                "The structured preference clock has too many origins"
            )
        }
        knownRevisions.forEach { (peerId, revision) ->
            validatePeerId(peerId)
            if (revision !in 0..MAX_SYNC_REVISION) {
                throw StructuredPreferenceSyncException(
                    "A structured preference revision is outside the supported range"
                )
            }
        }
    }

    fun validateChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ) {
        if (changes.size > MAX_STRUCTURED_PREFERENCE_CHANGES_PER_BATCH) {
            throw StructuredPreferenceSyncException(
                "Too many structured preference changes were sent"
            )
        }
        val revisions = hashSetOf<Pair<String, Long>>()
        changes.forEach { change ->
            validatePeerId(change.originPeerId)
            if (
                change.category != category ||
                change.originRevision !in 1..MAX_SYNC_REVISION ||
                change.lamportVersion !in 1..MAX_SYNC_REVISION
            ) {
                throw StructuredPreferenceSyncException(
                    "A structured preference change has an invalid category or version"
                )
            }
            if (!revisions.add(change.originPeerId to change.originRevision)) {
                throw StructuredPreferenceSyncException(
                    "A structured preference change was sent more than once"
                )
            }
            validateRecord(change)
        }
    }

    private fun validateRecord(change: StructuredPreferenceChange) {
        val record = change.record ?: throw StructuredPreferenceSyncException(
            "A structured preference change has no record data"
        )
        val populatedRecords = listOfNotNull(
            record.feedGroup,
            record.feedGroupMembership,
            record.feedGroupOrder,
            record.homeTab,
            record.homeTabOrder,
            record.channelProfileField,
            record.filterSet,
            record.portableSetting,
            record.completedDownload
        )
        if (populatedRecords.size != 1) {
            throw StructuredPreferenceSyncException(
                "Structured preference record data is ambiguous"
            )
        }
        when (change.recordType) {
            StructuredPreferenceRecordType.FEED_GROUP -> {
                requireCategory(change, StructuredPreferenceCategory.FEED_GROUPS)
                validateUuid(change.recordId)
                requireNoParent(change)
                val group = record.feedGroup ?: invalidRecord("Feed group data is missing")
                if (
                    group.name.isBlank() ||
                    group.name != group.name.trim() ||
                    group.name.length > MAX_STRUCTURED_NAME_LENGTH ||
                    group.iconId !in 0..MAX_FEED_GROUP_ICON_ID
                ) {
                    invalidRecord("Feed group data is invalid")
                }
            }

            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP -> {
                requireCategory(change, StructuredPreferenceCategory.FEED_GROUPS)
                val membership = record.feedGroupMembership
                    ?: invalidRecord("Feed group membership data is missing")
                val parent = change.parentRecordId
                    ?: invalidRecord("A feed group membership has no parent")
                validateUuid(parent)
                if (
                    membership.groupRecordId != parent ||
                    membership.serviceId < 0 ||
                    membership.subscriptionUrl.isBlank() ||
                    membership.subscriptionUrl != membership.subscriptionUrl.trim() ||
                    membership.subscriptionUrl.length > MAX_STRUCTURED_URL_LENGTH ||
                    change.recordId != StructuredPreferenceRecordId.feedGroupMembership(
                        parent,
                        membership.serviceId,
                        membership.subscriptionUrl
                    )
                ) {
                    invalidRecord("Feed group membership data is invalid")
                }
            }

            StructuredPreferenceRecordType.FEED_GROUP_ORDER -> {
                requireCategory(change, StructuredPreferenceCategory.FEED_GROUPS)
                requireUpsert(change)
                requireNoParent(change)
                if (change.recordId != StructuredPreferenceRecordId.feedGroupOrder()) {
                    invalidRecord("The feed group order identity is invalid")
                }
                val order = record.feedGroupOrder
                    ?: invalidRecord("Feed group order data is missing")
                validateOrder(order.groupRecordIds, MAX_FEED_GROUPS, ::validateUuid)
            }

            StructuredPreferenceRecordType.HOME_TAB -> {
                requireCategory(change, StructuredPreferenceCategory.HOME_TABS)
                requireNoParent(change)
                val tab = record.homeTab ?: invalidRecord("Home tab data is missing")
                validateHomeTab(tab)
                if (change.recordId != StructuredPreferenceRecordId.homeTab(tab)) {
                    invalidRecord("The home tab identity is invalid")
                }
            }

            StructuredPreferenceRecordType.HOME_TAB_ORDER -> {
                requireCategory(change, StructuredPreferenceCategory.HOME_TABS)
                requireUpsert(change)
                requireNoParent(change)
                if (change.recordId != StructuredPreferenceRecordId.homeTabOrder()) {
                    invalidRecord("The home tab order identity is invalid")
                }
                val order = record.homeTabOrder
                    ?: invalidRecord("Home tab order data is missing")
                if (order.tabRecordIds.isEmpty()) {
                    invalidRecord("At least one home tab is required")
                }
                validateOrder(order.tabRecordIds, MAX_HOME_TABS, ::validateDigest)
            }

            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD -> {
                requireCategory(change, StructuredPreferenceCategory.CHANNEL_PROFILES)
                requireNoParent(change)
                val field = record.channelProfileField
                    ?: invalidRecord("Channel profile data is missing")
                validateChannelProfileField(field)
                if (
                    change.recordId !=
                    StructuredPreferenceRecordId.channelProfileField(field)
                ) {
                    invalidRecord("The channel profile identity is invalid")
                }
            }

            StructuredPreferenceRecordType.FILTER_SET -> {
                requireCategory(change, StructuredPreferenceCategory.FILTERS)
                requireUpsert(change)
                requireNoParent(change)
                val filter = record.filterSet
                    ?: invalidRecord("Filter set data is missing")
                if (
                    filter.values.size > MAX_FILTER_VALUES ||
                    filter.values != filter.values.distinct().sorted() ||
                    filter.values.any {
                        it.isBlank() ||
                            it != it.trim() ||
                            it.length > MAX_FILTER_VALUE_LENGTH
                    } ||
                    change.recordId != StructuredPreferenceRecordId.filterSet(
                        filter.filterId
                    )
                ) {
                    invalidRecord("Filter set data is invalid")
                }
            }

            StructuredPreferenceRecordType.PORTABLE_SETTING -> {
                requireCategory(change, StructuredPreferenceCategory.SETTINGS)
                requireNoParent(change)
                val setting = record.portableSetting
                    ?: invalidRecord("Portable setting data is missing")
                validatePortableSetting(setting)
                if (
                    change.recordId !=
                    StructuredPreferenceRecordId.portableSetting(setting.settingId)
                ) {
                    invalidRecord("The portable setting identity is invalid")
                }
            }

            StructuredPreferenceRecordType.COMPLETED_DOWNLOAD -> {
                requireCategory(change, StructuredPreferenceCategory.COMPLETED_DOWNLOADS)
                requireNoParent(change)
                val download = record.completedDownload
                    ?: invalidRecord("Completed download data is missing")
                validateCompletedDownload(change, download)
            }
        }
    }

    private fun validatePortableSetting(setting: SyncedPortableSetting) {
        val populatedValues = listOfNotNull(
            setting.booleanValue,
            setting.stringValue,
            setting.floatValue
        )
        if (populatedValues.size != 1) {
            invalidRecord("Portable setting data is ambiguous")
        }
        when (setting.settingId.valueType) {
            PortableSettingValueType.BOOLEAN -> {
                if (
                    setting.booleanValue == null ||
                    setting.stringValue != null ||
                    setting.floatValue != null
                ) {
                    invalidRecord("Portable Boolean setting data is invalid")
                }
            }

            PortableSettingValueType.STRING -> {
                if (
                    setting.booleanValue != null ||
                    setting.stringValue.isNullOrBlank() ||
                    setting.stringValue != setting.stringValue.trim() ||
                    setting.stringValue.length > MAX_PORTABLE_SETTING_VALUE_LENGTH ||
                    setting.floatValue != null
                ) {
                    invalidRecord("Portable String setting data is invalid")
                }
            }

            PortableSettingValueType.FLOAT -> {
                val value = setting.floatValue
                if (
                    setting.booleanValue != null ||
                    setting.stringValue != null ||
                    value == null ||
                    !value.isFinite() ||
                    value !in MIN_PORTABLE_FLOAT_VALUE..MAX_PORTABLE_FLOAT_VALUE
                ) {
                    invalidRecord("Portable Float setting data is invalid")
                }
            }
        }
    }

    private fun validateCompletedDownload(
        change: StructuredPreferenceChange,
        download: SyncedCompletedDownload
    ) {
        validateUuid(download.syncId)
        validatePeerId(download.ownerPeerId)
        if (
            change.recordId != download.syncId ||
            change.originPeerId != download.ownerPeerId ||
            download.sourceUrl.isBlank() ||
            download.sourceUrl != download.sourceUrl.trim() ||
            download.sourceUrl.length > MAX_STRUCTURED_URL_LENGTH ||
            download.displayName.isBlank() ||
            download.displayName != download.displayName.trim() ||
            download.displayName.length > MAX_DOWNLOAD_DISPLAY_NAME_LENGTH ||
            download.mimeType.isBlank() ||
            download.mimeType != download.mimeType.trim() ||
            download.mimeType.length > MAX_DOWNLOAD_MIME_TYPE_LENGTH ||
            download.sizeBytes < 0 ||
            download.completedAtEpochMillis <= 0 ||
            download.mediaKind.length != 1 ||
            download.mediaKind[0] !in SUPPORTED_DOWNLOAD_KINDS
        ) {
            invalidRecord("Completed download data is invalid")
        }
    }

    private fun validateHomeTab(tab: SyncedHomeTab) {
        if (
            (tab.name?.length ?: 0) > MAX_STRUCTURED_NAME_LENGTH ||
            (tab.url?.length ?: 0) > MAX_STRUCTURED_URL_LENGTH ||
            tab.url?.let { it.isBlank() || it != it.trim() } == true ||
            (tab.kioskId?.length ?: 0) > MAX_FILTER_VALUE_LENGTH ||
            tab.kioskId?.let { it.isBlank() || it != it.trim() } == true
        ) {
            invalidRecord("Home tab data is invalid")
        }
        val noDetails = tab.serviceId == null &&
            tab.url == null &&
            tab.name == null &&
            tab.linkedRecordId == null &&
            tab.kioskId == null
        when (tab.type) {
            SyncedHomeTabType.BLANK,
            SyncedHomeTabType.DEFAULT_KIOSK,
            SyncedHomeTabType.SUBSCRIPTIONS,
            SyncedHomeTabType.FEED,
            SyncedHomeTabType.BOOKMARKS,
            SyncedHomeTabType.HISTORY,
            SyncedHomeTabType.DOWNLOADS -> {
                if (!noDetails) {
                    invalidRecord("A simple home tab has unexpected data")
                }
            }

            SyncedHomeTabType.KIOSK -> {
                if (
                    tab.serviceId == null ||
                    tab.serviceId < 0 ||
                    tab.kioskId.isNullOrBlank() ||
                    tab.url != null ||
                    tab.name != null ||
                    tab.linkedRecordId != null
                ) {
                    invalidRecord("Kiosk tab data is invalid")
                }
            }

            SyncedHomeTabType.CHANNEL,
            SyncedHomeTabType.REMOTE_PLAYLIST -> {
                if (
                    tab.serviceId == null ||
                    tab.serviceId < 0 ||
                    tab.url.isNullOrBlank() ||
                    tab.name == null ||
                    tab.linkedRecordId != null ||
                    tab.kioskId != null
                ) {
                    invalidRecord("Remote home tab data is invalid")
                }
            }

            SyncedHomeTabType.LOCAL_PLAYLIST -> {
                validateLinkedHomeTab(tab)
            }

            SyncedHomeTabType.FEED_GROUP -> {
                validateLinkedHomeTab(tab)
            }
        }
    }

    private fun validateLinkedHomeTab(tab: SyncedHomeTab) {
        val linkedRecordId = tab.linkedRecordId
            ?: invalidRecord("A linked home tab has no record identity")
        validateUuid(linkedRecordId)
        if (
            tab.name == null ||
            tab.serviceId != null ||
            tab.url != null ||
            tab.kioskId != null
        ) {
            invalidRecord("Linked home tab data is invalid")
        }
    }

    private fun validateChannelProfileField(field: SyncedChannelProfileField) {
        val serviceId = field.profileKey
            .removePrefix(CHANNEL_PROFILE_PREFIX)
            .substringBefore('.')
        if (
            field.profileKey.length > MAX_CHANNEL_PROFILE_KEY_LENGTH ||
            !CHANNEL_PROFILE_KEY.matches(field.profileKey) ||
            serviceId.toIntOrNull()?.toString() != serviceId
        ) {
            invalidRecord("Channel profile key is invalid")
        }
        when (field.field) {
            ChannelProfileField.SPEED -> {
                val speed = field.speed
                if (
                    speed == null ||
                    !speed.isFinite() ||
                    speed !in MIN_PROFILE_SPEED..MAX_PROFILE_SPEED ||
                    field.textValue != null
                ) {
                    invalidRecord("Channel speed profile data is invalid")
                }
            }

            ChannelProfileField.QUALITY -> {
                if (
                    field.speed != null ||
                    field.textValue.isNullOrBlank() ||
                    field.textValue.length > MAX_FILTER_VALUE_LENGTH
                ) {
                    invalidRecord("Channel quality profile data is invalid")
                }
            }

            ChannelProfileField.CAPTION -> {
                if (
                    field.speed != null ||
                    (field.textValue?.length ?: 0) > MAX_FILTER_VALUE_LENGTH
                ) {
                    invalidRecord("Channel caption profile data is invalid")
                }
            }
        }
    }

    private fun validateOrder(
        recordIds: List<String>,
        maximumSize: Int,
        validateIdentity: (String) -> Unit
    ) {
        if (
            recordIds.size > maximumSize ||
            recordIds.distinct().size != recordIds.size
        ) {
            invalidRecord("Structured preference order data is invalid")
        }
        recordIds.forEach(validateIdentity)
    }

    private fun requireCategory(
        change: StructuredPreferenceChange,
        category: StructuredPreferenceCategory
    ) {
        if (change.category != category) {
            invalidRecord("Structured preference data is in the wrong category")
        }
    }

    private fun requireNoParent(change: StructuredPreferenceChange) {
        if (change.parentRecordId != null) {
            invalidRecord("Structured preference data has an unexpected parent")
        }
    }

    private fun requireUpsert(change: StructuredPreferenceChange) {
        if (change.type != StructuredPreferenceChangeType.UPSERT) {
            invalidRecord("This structured preference record cannot be deleted")
        }
    }

    private fun validatePeerId(peerId: String) {
        try {
            PeerId.fromBase58(peerId)
        } catch (error: Exception) {
            throw StructuredPreferenceSyncException(
                "A structured preference synchronization PeerID is invalid",
                error
            )
        }
    }

    private fun validateUuid(value: String) {
        try {
            if (UUID.fromString(value).toString() != value) {
                throw IllegalArgumentException("Noncanonical UUID")
            }
        } catch (error: IllegalArgumentException) {
            throw StructuredPreferenceSyncException(
                "A structured preference UUID is invalid",
                error
            )
        }
    }

    private fun validateDigest(value: String) {
        if (!SHA_256_HEX.matches(value)) {
            invalidRecord("A structured preference record digest is invalid")
        }
    }

    private fun invalidRecord(message: String): Nothing {
        throw StructuredPreferenceSyncException(message)
    }

    private const val MAX_SYNC_ERROR_LENGTH = 512
    private const val MAX_FEED_GROUP_ICON_ID = 38
    private const val MIN_PROFILE_SPEED = 0.05F
    private const val MAX_PROFILE_SPEED = 10.0F
    private const val MIN_PORTABLE_FLOAT_VALUE = 0.05F
    private const val MAX_PORTABLE_FLOAT_VALUE = 10.0F
    private const val MAX_CHANNEL_PROFILE_KEY_LENGTH = 103
    private val SUPPORTED_DOWNLOAD_KINDS = setOf('a', 'v', 's', '?')
    private val CHANNEL_PROFILE_KEY = Regex(
        "^channel_playback_profile\\.v1\\.\\d+\\.[0-9a-f]{64}$"
    )
    private val SHA_256_HEX = Regex("^[0-9a-f]{64}$")
}

internal object StructuredPreferenceRecordId {
    fun initialFeedGroup(
        name: String,
        iconId: Int,
        duplicateOrdinal: Int
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "feed-group\u0000${name.trim()}\u0000$iconId\u0000$duplicateOrdinal"
                .toByteArray(Charsets.UTF_8)
        )
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(digest)
        return UUID(buffer.long, buffer.long).toString()
    }

    fun feedGroup(): String = UUID.randomUUID().toString()

    fun feedGroupMembership(
        groupRecordId: String,
        serviceId: Int,
        subscriptionUrl: String
    ): String {
        return digest(
            "feed-group-membership\u0000$groupRecordId\u0000$serviceId\u0000" +
                subscriptionUrl.trim()
        )
    }

    fun feedGroupOrder(): String = digest("feed-group-order")

    fun homeTab(tab: SyncedHomeTab): String {
        return digest(
            listOf(
                "home-tab",
                tab.type.name,
                tab.serviceId?.toString().orEmpty(),
                tab.url.orEmpty(),
                tab.name.orEmpty(),
                tab.linkedRecordId.orEmpty(),
                tab.kioskId.orEmpty()
            ).joinToString("\u0000")
        )
    }

    fun homeTabOrder(): String = digest("home-tab-order")

    fun channelProfileField(field: SyncedChannelProfileField): String {
        return digest("channel-profile\u0000${field.profileKey}\u0000${field.field.name}")
    }

    fun filterSet(filterId: StructuredFilterId): String {
        return digest("filter-set\u0000${filterId.name}")
    }

    fun portableSetting(settingId: PortableSettingId): String {
        return digest("portable-setting\u0000${settingId.name}")
    }

    private fun digest(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val item = byte.toInt() and 0xff
                append(HEX_DIGITS[item ushr 4])
                append(HEX_DIGITS[item and 0x0f])
            }
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}

class StructuredPreferenceSyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
