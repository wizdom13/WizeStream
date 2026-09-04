/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity

internal class PortableSettingsSyncAdapter(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.SETTINGS

    override fun snapshotHash(): String {
        val snapshot = portableSettingSpecs(context).mapNotNull(::currentPortableSetting)
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(snapshot)
        )
    }

    override fun reconcile(bootstrap: Boolean) {
        val desired = portableSettingSpecs(context).mapNotNull { spec ->
            currentPortableSetting(spec)?.let { setting ->
                StructuredPreferenceRecordId.portableSetting(setting.settingId) to setting
            }
        }.toMap()
        desired.forEach { (recordId, setting) ->
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.PORTABLE_SETTING,
                record = SyncedStructuredPreferenceRecord(portableSetting = setting)
            )
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.PORTABLE_SETTING
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    override fun materialize() {
        val specs = portableSettingSpecs(context).associateBy(PortableSettingSpec::id)
        val editor = preferences.edit()
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.PORTABLE_SETTING
        ).forEach { entity ->
            val setting = recordRepository.decodeRecord(entity).portableSetting
                ?: throw StructuredPreferenceSyncException(
                    "Stored portable setting data is invalid"
                )
            val spec = specs[setting.settingId]
                ?: throw StructuredPreferenceSyncException(
                    "Stored portable setting is not allowlisted"
                )
            if (entity.isDeleted) {
                editor.remove(spec.preferenceKey)
            } else {
                when (setting.settingId.valueType) {
                    PortableSettingValueType.BOOLEAN -> editor.putBoolean(
                        spec.preferenceKey,
                        requireNotNull(setting.booleanValue)
                    )

                    PortableSettingValueType.STRING -> editor.putString(
                        spec.preferenceKey,
                        requireNotNull(setting.stringValue)
                    )

                    PortableSettingValueType.FLOAT -> editor.putFloat(
                        spec.preferenceKey,
                        requireNotNull(setting.floatValue)
                    )
                }
            }
        }
        editor.apply()
    }

    private fun currentPortableSetting(
        spec: PortableSettingSpec
    ): SyncedPortableSetting? {
        val value = preferences.all[spec.preferenceKey] ?: return null
        return when (spec.id.valueType) {
            PortableSettingValueType.BOOLEAN -> (value as? Boolean)?.let {
                SyncedPortableSetting(spec.id, booleanValue = it)
            }

            PortableSettingValueType.STRING -> (value as? String)
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty() && it.length <= MAX_PORTABLE_SETTING_VALUE_LENGTH
                }
                ?.let { SyncedPortableSetting(spec.id, stringValue = it) }

            PortableSettingValueType.FLOAT -> (value as? Float)
                ?.takeIf { it.isFinite() }
                ?.let { SyncedPortableSetting(spec.id, floatValue = it) }
        }
    }
}
