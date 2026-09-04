/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity

internal class ChannelProfileSyncAdapter(
    private val preferences: SharedPreferences,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.CHANNEL_PROFILES

    override fun snapshotHash(): String {
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(currentChannelProfileFields())
        )
    }

    override fun reconcile(bootstrap: Boolean) {
        val desired = currentChannelProfileFields().associateBy {
            StructuredPreferenceRecordId.channelProfileField(it)
        }
        desired.forEach { (recordId, field) ->
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD,
                record = SyncedStructuredPreferenceRecord(channelProfileField = field)
            )
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    override fun materialize() {
        val fields = recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD
        )
        val editor = preferences.edit()
        preferences.all.keys
            .filter(::isChannelProfilePreferenceKey)
            .forEach(editor::remove)
        fields.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { recordRepository.decodeRecord(it).channelProfileField }
            .forEach { field ->
                val key = field.profileKey + field.field.preferenceSuffix
                when (field.field) {
                    ChannelProfileField.SPEED ->
                        editor.putFloat(key, requireNotNull(field.speed))

                    ChannelProfileField.QUALITY ->
                        editor.putString(key, requireNotNull(field.textValue))

                    ChannelProfileField.CAPTION ->
                        editor.putString(key, field.textValue.orEmpty())
                }
            }
        editor.commit()
    }

    private fun currentChannelProfileFields(): List<SyncedChannelProfileField> {
        return preferences.all.entries.mapNotNull { (key, value) ->
            if (!isChannelProfilePreferenceKey(key)) {
                return@mapNotNull null
            }
            val field = when {
                key.endsWith(SPEED_SUFFIX) -> ChannelProfileField.SPEED
                key.endsWith(QUALITY_SUFFIX) -> ChannelProfileField.QUALITY
                key.endsWith(CAPTION_SUFFIX) -> ChannelProfileField.CAPTION
                else -> return@mapNotNull null
            }
            val profileKey = key.removeSuffix(field.preferenceSuffix)
            when (field) {
                ChannelProfileField.SPEED -> (value as? Float)?.let {
                    SyncedChannelProfileField(
                        profileKey = profileKey,
                        field = field,
                        speed = it
                    )
                }

                ChannelProfileField.QUALITY -> (value as? String)
                    ?.takeIf(String::isNotBlank)
                    ?.let {
                        SyncedChannelProfileField(
                            profileKey = profileKey,
                            field = field,
                            textValue = it.take(MAX_FILTER_VALUE_LENGTH)
                        )
                    }

                ChannelProfileField.CAPTION -> (value as? String)?.let {
                    SyncedChannelProfileField(
                        profileKey = profileKey,
                        field = field,
                        textValue = it.take(MAX_FILTER_VALUE_LENGTH).ifEmpty { null }
                    )
                }
            }
        }.sortedWith(
            compareBy(
                SyncedChannelProfileField::profileKey,
                SyncedChannelProfileField::field
            )
        )
    }

    private fun isChannelProfilePreferenceKey(key: String): Boolean {
        return key.startsWith(CHANNEL_PROFILE_PREFIX) &&
            (
                key.endsWith(SPEED_SUFFIX) ||
                    key.endsWith(QUALITY_SUFFIX) ||
                    key.endsWith(CAPTION_SUFFIX)
                )
    }

    private val ChannelProfileField.preferenceSuffix: String
        get() = when (this) {
            ChannelProfileField.SPEED -> SPEED_SUFFIX
            ChannelProfileField.QUALITY -> QUALITY_SUFFIX
            ChannelProfileField.CAPTION -> CAPTION_SUFFIX
        }

    private companion object {
        const val SPEED_SUFFIX = ".speed"
        const val QUALITY_SUFFIX = ".quality"
        const val CAPTION_SUFFIX = ".caption"
    }
}
