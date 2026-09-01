/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.export

import android.content.Context
import android.graphics.Color
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig

internal class PipePipeSponsorBlockMigration(private val context: Context) {
    fun prepare(source: Map<String, *>): CompatibleSettingsMigration.Prepared {
        if (!containsRecognizedSetting(source)) {
            return CompatibleSettingsMigration.Prepared(emptyMap<String, Any>())
        }
        val values = buildMap<String, Any> {
            copyBoolean(source, context.getString(R.string.sponsor_block_enable_key))
            copyBoolean(source, context.getString(R.string.sponsor_block_notifications_key))
            copyBoolean(source, context.getString(R.string.sponsor_block_graced_rewind_key))

            SponsorBlockCategoryConfig.ALL.forEach { category ->
                val enabledKey = context.getString(category.enabledKeyResId)
                copyBoolean(source, enabledKey)

                val sourceColor = source["sponsor_block_category_${category.id}_color"]
                parseColor(sourceColor)?.let { put(category.colorKey(), it) }

                if (!category.isMarkerOnly()) {
                    val modeKey = "sponsor_block_category_${category.id}_mode"
                    convertBehavior(source[modeKey] as? String)?.let {
                        put(category.behaviorKey(), it.value)
                    }
                }
            }
        }
        return CompatibleSettingsMigration.Prepared(values)
    }

    private fun containsRecognizedSetting(source: Map<String, *>): Boolean {
        val globalKeys = setOf(
            context.getString(R.string.sponsor_block_enable_key),
            context.getString(R.string.sponsor_block_notifications_key),
            context.getString(R.string.sponsor_block_graced_rewind_key)
        )
        return source.keys.any { key ->
            key in globalKeys || SponsorBlockCategoryConfig.ALL.any { category ->
                key == context.getString(category.enabledKeyResId) ||
                    key == "sponsor_block_category_${category.id}_mode" ||
                    key == "sponsor_block_category_${category.id}_color"
            }
        }
    }

    private fun MutableMap<String, Any>.copyBoolean(
        source: Map<String, *>,
        key: String
    ) {
        (source[key] as? Boolean)?.let { put(key, it) }
    }

    private fun convertBehavior(value: String?): SponsorBlockBehavior? = when (value) {
        PIPEPIPE_AUTOMATIC -> SponsorBlockBehavior.SKIP
        PIPEPIPE_MANUAL -> SponsorBlockBehavior.MANUAL
        PIPEPIPE_HIGHLIGHT -> SponsorBlockBehavior.DONT_SKIP
        else -> null
    }

    private fun parseColor(value: Any?): Long? {
        if (value !is String || value.length > MAX_COLOR_LENGTH) {
            return null
        }
        return runCatching {
            Color.parseColor(value.trim()).toLong() and 0xFFFFFFFFL
        }.getOrNull()
    }

    companion object {
        private const val PIPEPIPE_AUTOMATIC = "automatic"
        private const val PIPEPIPE_MANUAL = "manual"
        private const val PIPEPIPE_HIGHLIGHT = "highlight"
        private const val MAX_COLOR_LENGTH = 32
    }
}
