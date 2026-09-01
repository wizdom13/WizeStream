/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.export

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.IOException
import org.schabi.newpipe.sync.MAX_PORTABLE_SETTING_VALUE_LENGTH
import org.schabi.newpipe.sync.PortableSettingId
import org.schabi.newpipe.sync.PortableSettingValueType
import org.schabi.newpipe.sync.portableSettingSpecs

internal class CompatibleSettingsMigration(
    context: Context,
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
) {
    private val specsByKey = portableSettingSpecs(context)
        // Service identifiers are portable between WizeStream devices, but not necessarily
        // between NewPipe derivatives whose service sets and identifiers may differ.
        .filterNot { it.id == PortableSettingId.SERVICE }
        .associateBy { it.preferenceKey }

    data class Prepared internal constructor(
        internal val values: Map<String, Any>
    ) {
        val size: Int
            get() = values.size
    }

    data class Rollback internal constructor(
        internal val importedKeys: Set<String>,
        internal val previousValues: Map<String, Any>
    )

    fun prepare(source: Map<String, *>): Prepared {
        val compatible = buildMap {
            source.forEach { (key, value) ->
                val spec = specsByKey[key] ?: return@forEach
                val normalized = when (spec.id.valueType) {
                    PortableSettingValueType.BOOLEAN -> value as? Boolean

                    PortableSettingValueType.STRING -> (value as? String)
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty() &&
                                it.length <= MAX_PORTABLE_SETTING_VALUE_LENGTH
                        }

                    PortableSettingValueType.FLOAT -> (value as? Float)
                        ?.takeIf { it.isFinite() }
                }
                if (normalized != null) {
                    put(key, normalized)
                }
            }
        }
        return Prepared(compatible)
    }

    @Throws(IOException::class)
    fun apply(prepared: Prepared): Rollback {
        val importedKeys = prepared.values.keys
        val previousValues: Map<String, Any> = preferences.all.entries
            .mapNotNull { (key, value) ->
                value?.takeIf { key in importedKeys }?.let { key to it }
            }
            .toMap()
        if (!write(prepared.values, importedKeysToRemove = emptySet())) {
            throw IOException("Unable to commit migrated settings")
        }
        return Rollback(importedKeys, previousValues)
    }

    @Throws(IOException::class)
    fun rollback(rollback: Rollback) {
        if (!write(rollback.previousValues, rollback.importedKeys)) {
            throw IOException("Unable to roll back migrated settings")
        }
    }

    private fun write(
        values: Map<String, *>,
        importedKeysToRemove: Set<String>
    ): Boolean {
        val editor = preferences.edit()
        importedKeysToRemove.forEach(editor::remove)
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>().toSet()
                    if (strings.size == value.size) {
                        editor.putStringSet(key, strings)
                    }
                }
            }
        }
        return editor.commit()
    }
}
