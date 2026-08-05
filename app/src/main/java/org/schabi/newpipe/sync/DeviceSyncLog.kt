/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class DeviceSyncLogCategory {
    SUBSCRIPTIONS,
    PLAYLISTS,
    WATCH_HISTORY,
    SEARCH_HISTORY,
    LEARNING_NOTES,
    FEED_GROUPS,
    HOME_TABS,
    CHANNEL_PROFILES,
    FILTERS,
    SETTINGS,
    COMPLETED_DOWNLOADS
}

@Serializable
enum class DeviceSyncLogStatus {
    SUCCEEDED,
    FAILED,
    DISABLED
}

@Serializable
data class DeviceSyncLogCategoryResult(
    val category: DeviceSyncLogCategory,
    val status: DeviceSyncLogStatus,
    val sentChanges: Int = 0,
    val receivedChanges: Int = 0,
    val error: String? = null
)

@Serializable
data class DeviceSyncLogAttempt(
    val deviceName: String,
    val peerId: String,
    val addresses: List<String>,
    val categories: List<DeviceSyncLogCategoryResult>
)

@Serializable
data class DeviceSyncLogEntry(
    val timestampEpochMillis: Long,
    val background: Boolean,
    val succeededDevices: Int,
    val failedDevices: Int,
    val sentChanges: Int,
    val receivedChanges: Int,
    val localAddresses: List<String> = emptyList(),
    val attempts: List<DeviceSyncLogAttempt> = emptyList(),
    val fatalError: String? = null
)

class DeviceSyncLogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun entries(): List<DeviceSyncLogEntry> {
        val stored = preferences.getString(ENTRIES_KEY, null) ?: return emptyList()
        return runCatching {
            JSON.decodeFromString<List<DeviceSyncLogEntry>>(stored)
        }.getOrDefault(emptyList())
    }

    fun record(
        summary: DeviceSyncSummary,
        background: Boolean,
        localAddresses: List<String>
    ) {
        save(
            DeviceSyncLogEntry(
                timestampEpochMillis = System.currentTimeMillis(),
                background = background,
                succeededDevices = summary.succeeded,
                failedDevices = summary.failed,
                sentChanges = summary.sentChanges,
                receivedChanges = summary.receivedChanges,
                localAddresses = localAddresses,
                attempts = summary.attempts.map { it.toLogAttempt() }
            )
        )
    }

    fun recordFailure(background: Boolean, error: Throwable) {
        save(
            DeviceSyncLogEntry(
                timestampEpochMillis = System.currentTimeMillis(),
                background = background,
                succeededDevices = 0,
                failedDevices = 0,
                sentChanges = 0,
                receivedChanges = 0,
                fatalError = error.diagnosticMessage()
            )
        )
    }

    fun clear() {
        preferences.edit().remove(ENTRIES_KEY).apply()
    }

    private fun save(entry: DeviceSyncLogEntry) {
        val updated = buildList {
            add(entry)
            addAll(entries())
        }.take(MAX_ENTRIES)
        preferences.edit()
            .putString(ENTRIES_KEY, JSON.encodeToString(updated))
            .apply()
    }

    private fun DeviceSyncAttempt.toLogAttempt() = DeviceSyncLogAttempt(
        deviceName = peer.deviceName,
        peerId = peer.peerId,
        addresses = peer.addresses,
        categories = buildList {
            add(
                categoryResult(
                    DeviceSyncLogCategory.SUBSCRIPTIONS,
                    result?.sentChanges,
                    result?.receivedChanges,
                    error
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.PLAYLISTS,
                    playlistResult?.sentChanges,
                    playlistResult?.receivedChanges,
                    playlistError
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.WATCH_HISTORY,
                    watchHistoryResult?.sentChanges,
                    watchHistoryResult?.receivedChanges,
                    watchHistoryError,
                    watchHistorySkipped
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.SEARCH_HISTORY,
                    searchHistoryResult?.sentChanges,
                    searchHistoryResult?.receivedChanges,
                    searchHistoryError,
                    searchHistorySkipped
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.LEARNING_NOTES,
                    learningNotesResult?.sentChanges,
                    learningNotesResult?.receivedChanges,
                    learningNotesError,
                    learningNotesSkipped
                )
            )
            StructuredPreferenceCategory.entries.forEach { category ->
                val syncResult = structuredPreferenceResults[category]
                add(
                    categoryResult(
                        category.toLogCategory(),
                        syncResult?.sentChanges,
                        syncResult?.receivedChanges,
                        structuredPreferenceErrors[category]
                    )
                )
            }
        }
    )

    private fun categoryResult(
        category: DeviceSyncLogCategory,
        sentChanges: Int?,
        receivedChanges: Int?,
        error: String?,
        disabled: Boolean = false
    ): DeviceSyncLogCategoryResult {
        return when {
            disabled -> DeviceSyncLogCategoryResult(
                category = category,
                status = DeviceSyncLogStatus.DISABLED
            )

            sentChanges != null && receivedChanges != null -> DeviceSyncLogCategoryResult(
                category = category,
                status = DeviceSyncLogStatus.SUCCEEDED,
                sentChanges = sentChanges,
                receivedChanges = receivedChanges
            )

            else -> DeviceSyncLogCategoryResult(
                category = category,
                status = DeviceSyncLogStatus.FAILED,
                error = error
            )
        }
    }

    private fun StructuredPreferenceCategory.toLogCategory(): DeviceSyncLogCategory {
        return when (this) {
            StructuredPreferenceCategory.FEED_GROUPS -> DeviceSyncLogCategory.FEED_GROUPS

            StructuredPreferenceCategory.HOME_TABS -> DeviceSyncLogCategory.HOME_TABS

            StructuredPreferenceCategory.CHANNEL_PROFILES ->
                DeviceSyncLogCategory.CHANNEL_PROFILES

            StructuredPreferenceCategory.FILTERS -> DeviceSyncLogCategory.FILTERS

            StructuredPreferenceCategory.SETTINGS -> DeviceSyncLogCategory.SETTINGS

            StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                DeviceSyncLogCategory.COMPLETED_DOWNLOADS
        }
    }

    private fun Throwable.diagnosticMessage(): String {
        return generateSequence(this) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .joinToString(CAUSE_SEPARATOR) { cause ->
                val name = cause.javaClass.simpleName
                cause.message?.takeIf(String::isNotBlank)?.let { "$name: $it" } ?: name
            }
            .take(MAX_ERROR_LENGTH)
    }

    companion object {
        private const val PREFERENCES_NAME = "device_sync_log"
        private const val ENTRIES_KEY = "entries"
        private const val MAX_ENTRIES = 20
        private const val MAX_CAUSE_DEPTH = 4
        private const val MAX_ERROR_LENGTH = 2_048
        private const val CAUSE_SEPARATOR = " → "
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
