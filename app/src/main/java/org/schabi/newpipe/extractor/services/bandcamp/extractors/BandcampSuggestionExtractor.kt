/*
 * SPDX-FileCopyrightText: 2019-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.bandcamp.extractors

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import java.io.IOException
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

class BandcampSuggestionExtractor(service: StreamingService) : SuggestionExtractor(service) {
    @Throws(IOException::class, ExtractionException::class)
    override fun suggestionList(query: String): List<String> {
        val payload = JsonWriter.string().`object`()
            .value("fan_id", null as String?)
            .value("full_page", false)
            .value("search_filter", "")
            .value("search_text", query)
            .end().done().toByteArray(Charsets.UTF_8)
        val response = NewPipe.getDownloader().post(
            BandcampExtractorHelper.BASE_API_URL + "/bcsearch_public_api/1/autocomplete_elastic",
            mapOf("Content-Type" to listOf("application/json")),
            payload
        )
        return try {
            JsonParser.`object`().from(response.responseBody())
                .getObject("auto")?.getArray("results")
                ?.filterIsInstance<JsonObject>()
                ?.mapNotNull { it.getString("name") }
                ?.distinct().orEmpty()
        } catch (_: JsonParserException) {
            emptyList()
        }
    }
}
