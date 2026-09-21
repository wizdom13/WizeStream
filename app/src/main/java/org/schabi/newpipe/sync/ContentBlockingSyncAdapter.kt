/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.R
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.util.ContentBlockingHelper

/**
 * Synchronizes user-owned content-blocking rules and behavior.
 *
 * Blocked videos, channels, and keywords are separate records so concurrent changes on two
 * devices merge instead of replacing the other device's whole list. AiSList downloaded data is
 * intentionally excluded; only the user's enable/behavior choices are synchronized.
 */
internal class ContentBlockingSyncAdapter(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.CONTENT_BLOCKING

    override fun snapshotHash(): String {
        val snapshot = ContentBlockingSnapshot(
            state = currentState(),
            entries = currentEntries().sortedWith(
                compareBy(
                    SyncedContentBlockEntry::kind,
                    SyncedContentBlockEntry::key,
                    SyncedContentBlockEntry::label
                )
            )
        )
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(snapshot)
        )
    }

    override fun reconcile(bootstrap: Boolean) {
        val state = currentState()
        recordRepository.saveLocalUpsert(
            category = category,
            recordId = StructuredPreferenceRecordId.contentBlockingState(),
            recordType = StructuredPreferenceRecordType.CONTENT_BLOCKING_STATE,
            record = SyncedStructuredPreferenceRecord(contentBlockingState = state)
        )

        val desired = currentEntries().associateBy { entry ->
            StructuredPreferenceRecordId.contentBlockEntry(entry.kind, entry.key)
        }
        desired.forEach { (recordId, entry) ->
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.CONTENT_BLOCK_ENTRY,
                record = SyncedStructuredPreferenceRecord(contentBlockEntry = entry)
            )
        }

        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.CONTENT_BLOCK_ENTRY
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    override fun materialize() {
        val state = recordRepository.getRecord(
            category,
            StructuredPreferenceRecordId.contentBlockingState()
        )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
            ?.let(recordRepository::decodeRecord)
            ?.contentBlockingState
            ?: currentState()

        val entries = recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.CONTENT_BLOCK_ENTRY
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { recordRepository.decodeRecord(it).contentBlockEntry }

        val blockedVideos = entries
            .filter { it.kind == ContentBlockEntryKind.VIDEO }
            .map(::encodeStoredEntry)
            .toSet()
        val blockedChannels = entries
            .filter { it.kind == ContentBlockEntryKind.CHANNEL }
            .map(::encodeStoredEntry)
            .toSet()
        val blockedKeywords = entries
            .filter { it.kind == ContentBlockEntryKind.KEYWORD }
            .sortedBy { it.key }
            .joinToString("\n") { it.label }

        preferences.edit()
            .putBoolean(
                context.getString(R.string.content_blocking_enabled_key),
                state.enabled
            )
            .putStringSet(
                context.getString(R.string.content_blocking_targets_key),
                state.targets.toSet()
            )
            .putBoolean(
                context.getString(R.string.aislist_enabled_key),
                state.aiSListEnabled
            )
            .putString(
                context.getString(R.string.aislist_warn_behavior_key),
                state.aiSListWarnBehavior
            )
            .putStringSet(
                context.getString(R.string.blocked_videos_key),
                blockedVideos
            )
            .putStringSet(
                context.getString(R.string.blocked_channels_key),
                blockedChannels
            )
            .putString(
                context.getString(R.string.blocked_keywords_key),
                blockedKeywords
            )
            .commit()
    }

    private fun currentState(): SyncedContentBlockingState {
        val targetsKey = context.getString(R.string.content_blocking_targets_key)
        val targets = if (preferences.contains(targetsKey)) {
            preferences.getStringSet(targetsKey, CONTENT_BLOCKING_TARGETS)?.toSet()
                ?: CONTENT_BLOCKING_TARGETS
        } else {
            CONTENT_BLOCKING_TARGETS
        }
        val warnBehavior = preferences.getString(
            context.getString(R.string.aislist_warn_behavior_key),
            context.getString(R.string.aislist_warn_behavior_label_value)
        ) ?: context.getString(R.string.aislist_warn_behavior_label_value)

        return SyncedContentBlockingState(
            enabled = preferences.getBoolean(
                context.getString(R.string.content_blocking_enabled_key),
                true
            ),
            targets = targets.sorted(),
            aiSListEnabled = preferences.getBoolean(
                context.getString(R.string.aislist_enabled_key),
                false
            ),
            aiSListWarnBehavior = warnBehavior
        )
    }

    private fun currentEntries(): List<SyncedContentBlockEntry> {
        return buildList {
            addAll(
                storedEntries(
                    context.getString(R.string.blocked_videos_key),
                    ContentBlockEntryKind.VIDEO
                )
            )
            addAll(
                storedEntries(
                    context.getString(R.string.blocked_channels_key),
                    ContentBlockEntryKind.CHANNEL
                )
            )
            val keywords = ContentBlockingHelper.sanitizeKeywords(
                preferences.getString(
                    context.getString(R.string.blocked_keywords_key),
                    ""
                )
            )
            if (keywords.isNotEmpty()) {
                keywords.split("\n").forEach { keyword ->
                    val label = keyword.trim()
                    add(
                        SyncedContentBlockEntry(
                            kind = ContentBlockEntryKind.KEYWORD,
                            key = canonicalKey(label),
                            label = label
                        )
                    )
                }
            }
        }
    }

    private fun storedEntries(
        preferenceKey: String,
        kind: ContentBlockEntryKind
    ): List<SyncedContentBlockEntry> {
        return preferences.getStringSet(preferenceKey, emptySet())
            .orEmpty()
            .mapNotNull { value ->
                val separator = value.indexOf(ENTRY_SEPARATOR)
                val rawKey = if (separator < 0) value else value.substring(0, separator)
                val key = canonicalKey(rawKey)
                if (key.isEmpty()) {
                    return@mapNotNull null
                }
                val rawLabel = if (separator < 0) rawKey else value.substring(separator + 1)
                val label = rawLabel.trim().ifEmpty { key }
                SyncedContentBlockEntry(kind, key, label)
            }
            .distinctBy { it.kind to it.key }
    }

    private fun encodeStoredEntry(entry: SyncedContentBlockEntry): String {
        return entry.key + ENTRY_SEPARATOR + entry.label.replace('\t', ' ').trim()
    }

    private fun canonicalKey(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    @Serializable
    private data class ContentBlockingSnapshot(
        val state: SyncedContentBlockingState,
        val entries: List<SyncedContentBlockEntry>
    )

    companion object {
        private const val ENTRY_SEPARATOR = "\t"
    }
}
