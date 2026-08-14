/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

internal object DesktopSyncCategories {
    val ordered = listOf(
        "subscriptions", "playlists", "watchHistory", "searchHistory", "learningNotes",
        "feedGroups", "homeTabs", "channelProfiles", "filters", "settings",
        "completedDownloads"
    )
    val automaticDefaults = ordered.filterNot { it == "searchHistory" }
}

internal data class AutomaticSyncPolicy(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val categories: List<String> = DesktopSyncCategories.automaticDefaults,
    val peerIds: List<String> = emptyList(),
    val updatedAtEpochMillis: Long = 0
) {
    fun validate() {
        require(intervalMinutes in 15..1_440) { "Automatic sync interval must be between 15 and 1440 minutes" }
        require(categories.isNotEmpty() || !enabled) { "Select at least one automatic sync category" }
        require(peerIds.isNotEmpty() || !enabled) { "Select at least one trusted device" }
        require(categories.distinct().size == categories.size) { "Duplicate synchronization category" }
        require(categories.all(DesktopSyncCategories.ordered::contains)) { "Unknown synchronization category" }
        require(peerIds.distinct().size == peerIds.size) { "Duplicate trusted device" }
        require(peerIds.all { it.isNotBlank() && it.length <= 160 }) { "Invalid trusted device" }
    }

    fun asMap(): Map<String, Any> = linkedMapOf(
        "enabled" to enabled,
        "intervalMinutes" to intervalMinutes,
        "categories" to categories,
        "peerIds" to peerIds,
        "localNetworkOnly" to true,
        "updatedAtEpochMillis" to updatedAtEpochMillis
    )
}

internal enum class SyncRunTrigger { MANUAL, AUTOMATIC }

internal enum class SyncRunOutcome {
    RUNNING, SUCCESS, PARTIAL, FAILED, SKIPPED_OFFLINE, SKIPPED_BUSY,
    SKIPPED_NO_DEVICES, SKIPPED_BACKOFF
}
