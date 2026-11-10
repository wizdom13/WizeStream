package org.schabi.newpipe.local.radio

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException

internal data class RadioFilters(
    val name: String = "",
    val country: String = "",
    val language: String = "",
    val genre: String = ""
)

internal object RadioDirectory {
    private val mirrors = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info")
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun search(filters: RadioFilters): List<RadioStation> = query({ mirror -> searchUrl(mirror, filters) }, ::parse)

    fun filterOptions(filter: RadioFilter): List<RadioFilterOption> {
        if (filter == RadioFilter.COUNTRY) return radioCountries()
        val endpoint = if (filter == RadioFilter.LANGUAGE) "languages" else "tags"
        return query(
            { mirror ->
                mirror.toHttpUrl().newBuilder()
                    .addPathSegments("json/$endpoint")
                    .addQueryParameter("hidebroken", "true")
                    .addQueryParameter("order", "stationcount")
                    .addQueryParameter("reverse", "true")
                    .addQueryParameter("limit", "1000")
                    .build()
            },
            ::parseFilterOptions
        )
    }

    private fun <T> query(url: (String) -> HttpUrl, parseResponse: (String) -> T): T {
        var failure: IOException? = null
        for (mirror in mirrors) {
            val request = Request.Builder()
                .url(url(mirror))
                .header("User-Agent", "WizeStream (https://github.com/wizdom13/WizeStream)")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Radio Browser returned HTTP ${response.code}")
                    return parseResponse(response.body.string())
                }
            } catch (error: IOException) {
                failure = error
            } catch (error: JSONException) {
                failure = IOException("Invalid station directory response", error)
            }
        }
        throw IOException("Radio Browser is unavailable", failure)
    }

    internal fun searchUrl(mirror: String, filters: RadioFilters): HttpUrl = mirror.toHttpUrl().newBuilder()
        .addPathSegments("json/stations/search")
        .addQueryParameter("hidebroken", "true")
        .addQueryParameter("limit", "100")
        .addQueryParameter("order", "clickcount")
        .addQueryParameter("reverse", "true")
        .apply {
            filters.name.trim().takeIf(String::isNotEmpty)?.let { addQueryParameter("name", it) }
            filters.country.trim().takeIf(String::isNotEmpty)?.let {
                val code = countryCode(it)
                if (code == null) addQueryParameter("country", it) else addQueryParameter("countrycode", code)
            }
            filters.language.trim().takeIf(String::isNotEmpty)?.let { addQueryParameter("language", directoryLanguage(it)) }
            filters.genre.trim().takeIf(String::isNotEmpty)?.let { addQueryParameter("tag", it.lowercase(Locale.ROOT)) }
        }
        .build()

    private fun countryCode(country: String): String? = Locale.getISOCountries().firstOrNull { code ->
        val region = Locale.Builder().setRegion(code).build()
        country.equals(code, ignoreCase = true) ||
            country.equals(region.getDisplayCountry(Locale.ENGLISH), ignoreCase = true) ||
            country.equals(region.displayCountry, ignoreCase = true)
    }

    private fun directoryLanguage(language: String): String {
        val code = language.lowercase(Locale.ROOT)
        return if (code in Locale.getISOLanguages()) {
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
        } else {
            code
        }
    }

    private fun parseFilterOptions(json: String): List<RadioFilterOption> {
        val options = JSONArray(json)
        return (0 until options.length()).mapNotNull { index ->
            val row = options.optJSONObject(index) ?: return@mapNotNull null
            val value = row.optString("name").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val label = value.replaceFirstChar { it.titlecase(Locale.ROOT) }
            RadioFilterOption(value, label, row.optString("iso_639"))
        }.distinctBy { it.value }.sortedBy { it.label }
    }

    internal fun parse(json: String): List<RadioStation> {
        val stations = JSONArray(json)
        return (0 until stations.length()).mapNotNull { index ->
            val row = stations.optJSONObject(index) ?: return@mapNotNull null
            val name = row.optString("name").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            // The original URL can refresh expiring tokens when the broadcaster redirects it.
            val url = secureRadioUrl(row.optString("url")) ?: secureRadioUrl(row.optString("url_resolved"))
                ?: return@mapNotNull null
            val details = listOf("country", "language", "tags")
                .map { row.optString(it).trim() }
                .filter(String::isNotEmpty)
                .joinToString(" · ")
            RadioStation(name, url, details, row.optInt("hls") == 1 || isRadioHls(url), secureRadioUrl(row.optString("favicon")))
        }.distinctBy(RadioStation::url)
    }
}
