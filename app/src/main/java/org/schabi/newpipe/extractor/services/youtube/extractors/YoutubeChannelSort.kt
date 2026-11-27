/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.exceptions.ParsingException

/** Reads ordering commands separately from the channel's membership visibility filters. */
object YoutubeChannelSort {
    private const val SORT_OPTION_COUNT = 3
    private const val DROPDOWN = "CHIP_VIEW_MODEL_DISPLAY_TYPE_DROP_DOWN"

    @JvmStatic
    @Throws(ParsingException::class)
    fun continuationToken(tab: JsonObject, sortIndex: Int): String {
        if (sortIndex !in 0 until SORT_OPTION_COUNT) {
            throw ParsingException("Unsupported YouTube channel tab sort index: $sortIndex")
        }
        val chips = tab.getObject("content").getObject("richGridRenderer")
            .getObject("header").getObject("chipBarViewModel").getArray("chips")
        val dropdowns = chips.filterIsInstance<JsonObject>()
            .map { it.getObject("chipViewModel") }
            .filter { chip ->
                chip.getString("displayType") == DROPDOWN ||
                    chip.getObject("tapCommand").getObject("innertubeCommand").has("showSheetCommand")
            }

        val command = if (dropdowns.isNotEmpty()) {
            // Once a dropdown is present, adjacent chips can mean Members only/Public.
            // An unsupported dropdown must never fall back to their positional tokens.
            if (dropdowns.size != 1) {
                throw ParsingException("YouTube channel tab sort dropdown is ambiguous")
            }
            val options = dropdowns.single().getObject("tapCommand").getObject("innertubeCommand")
                .getObject("showSheetCommand").getObject("panelLoadingStrategy")
                .getObject("inlineContent").getObject("sheetViewModel").getObject("content")
                .getObject("listViewModel").getArray("listItems")
            // YouTube keeps Latest/Popular/Oldest in this order inside the sort sheet.
            // Titles AND entity keys are localized, so neither is a stable identifier.
            if (options.size != SORT_OPTION_COUNT || options.any {
                    it !is JsonObject || it.getObject("listItemViewModel").isEmpty()
                }
            ) {
                throw ParsingException("YouTube channel tab sort filter is not available")
            }
            options.getObject(sortIndex).getObject("listItemViewModel")
                .getObject("rendererContext").getObject("commandContext")
                .getObject("onTap").getObject("innertubeCommand")
        } else {
            if (chips.size <= sortIndex) {
                throw ParsingException("YouTube channel tab sort filter is not available")
            }
            chips.getObject(sortIndex).getObject("chipViewModel")
                .getObject("tapCommand").getObject("innertubeCommand")
        }

        val tokens = linkedSetOf<String>()
        collectTokens(command, tokens)
        return tokens.singleOrNull()
            ?: throw ParsingException("Could not get an unambiguous YouTube channel tab sort continuation")
    }

    private fun collectTokens(command: JsonObject, tokens: MutableSet<String>) {
        command.getObject("continuationCommand").getString("token")
            ?.takeIf(String::isNotBlank)?.let(tokens::add)
        // The sheet also updates its label via entityUpdateCommand. Only follow the
        // executable command list; unrelated payloads may contain other endpoints.
        command.getObject("commandExecutorCommand").getArray("commands")
            .filterIsInstance<JsonObject>().forEach { collectTokens(it, tokens) }
    }
}
