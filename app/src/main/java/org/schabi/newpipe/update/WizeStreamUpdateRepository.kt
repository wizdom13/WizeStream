package org.schabi.newpipe.update

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.DownloaderImpl

object WizeStreamUpdateRepository {
    const val RELEASES_URL = "https://api.github.com/repos/wizdom13/WizeStream/releases"
    private const val MAX_CHANGELOG_RELEASES = 20

    enum class VersionComparison {
        NEWER,
        SAME_OR_OLDER,
        UNKNOWN
    }

    data class Release(
        val version: String,
        val title: String,
        val htmlUrl: String,
        val body: String,
        val publishedAt: String,
        val draft: Boolean,
        val prerelease: Boolean,
        val apkUrl: String?,
        val apkName: String? = null,
        val apkSize: Long? = null
    )

    fun fetchLatestCandidateRelease(): Release? {
        return selectLatestCandidateRelease(fetchReleases())
    }

    fun fetchReleases(): List<Release> {
        val json = DownloaderImpl.getInstance().get(RELEASES_URL).responseBody()
        return parseReleases(json)
    }

    fun parseReleases(json: String): List<Release> {
        val array = JsonParser.array().from(json)
        return array
            .filterIsInstance<JsonObject>()
            .map(::parseReleaseObject)
            .filter { !it.draft && it.version.isNotBlank() }
    }

    fun selectLatestCandidateRelease(releases: List<Release>): Release? {
        return releases
            .filter { !it.draft && it.version.isNotBlank() }
            .maxByOrNull { parsePublishedAt(it.publishedAt) }
    }

    fun formatChangelog(releases: List<Release>, emptyText: String): String {
        val visibleReleases = releases
            .filter { !it.draft }
            .sortedByDescending { parsePublishedAt(it.publishedAt) }
            .take(MAX_CHANGELOG_RELEASES)
        if (visibleReleases.isEmpty()) {
            return emptyText
        }

        return visibleReleases
            .joinToString(separator = "\n\n────────────\n\n") { release ->
                buildString {
                    append(release.title.ifBlank { release.version })
                    if (release.publishedAt.isNotBlank()) {
                        append("\n")
                        append(release.publishedAt.substringBefore('T'))
                    }
                    append("\n\n")
                    append(release.body.ifBlank { emptyText })
                }
            }
    }

    fun installedVersionName(): String {
        return BuildConfig.WIZESTREAM_VERSION_NAME.ifBlank { BuildConfig.VERSION_NAME }
    }

    fun installedVersionSummary(): String {
        return "${installedVersionName()} (code ${BuildConfig.VERSION_CODE})"
    }

    fun compareInstalledToLatest(installed: String, latest: String): VersionComparison {
        val installedVersion = SemanticVersion.parse(installed) ?: return VersionComparison.UNKNOWN
        val latestVersion = SemanticVersion.parse(latest) ?: return VersionComparison.UNKNOWN
        val compare = latestVersion.compareTo(installedVersion)
        return if (compare > 0) VersionComparison.NEWER else VersionComparison.SAME_OR_OLDER
    }

    private fun parsePublishedAt(publishedAt: String): Instant {
        return try {
            if (publishedAt.isBlank()) {
                Instant.EPOCH
            } else {
                Instant.parse(publishedAt)
            }
        } catch (e: DateTimeParseException) {
            Instant.EPOCH
        }
    }

    private fun parseReleaseObject(json: JsonObject): Release {
        val tagName = json.getString("tag_name", "") ?: ""
        val name = json.getString("name", "") ?: ""
        val htmlUrl = json.getString("html_url", "") ?: ""
        val body = json.getString("body", "") ?: ""
        val publishedAt = json.getString("published_at", "") ?: ""
        val assets = json.getArray("assets", JsonArray()) ?: JsonArray()
        val apkAsset = findApkAsset(assets)
        return Release(
            version = tagName.ifBlank { name },
            title = name.ifBlank { tagName },
            htmlUrl = htmlUrl,
            body = body,
            publishedAt = publishedAt,
            draft = json.getBoolean("draft", false),
            prerelease = json.getBoolean("prerelease", false),
            apkUrl = apkAsset?.getString("browser_download_url", null),
            apkName = apkAsset?.getString("name", null),
            apkSize = apkAsset?.getLong("size", -1)?.takeIf { it >= 0 }
        )
    }

    private fun findApkAsset(assets: JsonArray): JsonObject? {
        return assets
            .filterIsInstance<JsonObject>()
            .firstOrNull { asset ->
                val name = asset.getString("name", "")?.lowercase(Locale.ROOT) ?: ""
                name.endsWith(".apk")
            }
    }

    private data class SemanticVersion(
        val numbers: List<Int>,
        val suffixPrefix: String,
        val suffixNumber: Int?
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            val maxSize = maxOf(numbers.size, other.numbers.size)
            for (index in 0 until maxSize) {
                val current = numbers.getOrElse(index) { 0 }
                val otherValue = other.numbers.getOrElse(index) { 0 }
                if (current != otherValue) {
                    return current.compareTo(otherValue)
                }
            }

            if (suffixPrefix != other.suffixPrefix) {
                return suffixPrefix.compareTo(other.suffixPrefix)
            }

            return when {
                suffixNumber == null && other.suffixNumber == null -> 0
                suffixNumber == null -> -1
                other.suffixNumber == null -> 1
                else -> suffixNumber.compareTo(other.suffixNumber)
            }
        }

        companion object {
            private val VERSION_REGEX = Regex("^v?(\\d+(?:\\.\\d+)*)(?:[-_]?([a-zA-Z]+)(\\d+)?)?$")

            fun parse(raw: String): SemanticVersion? {
                val match = VERSION_REGEX.matchEntire(raw.trim()) ?: return null
                val numbers = match.groupValues[1]
                    .split('.')
                    .map { it.toIntOrNull() ?: return null }
                val suffixPrefix = match.groupValues[2].lowercase(Locale.ROOT)
                val suffixNumber = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
                return SemanticVersion(numbers, suffixPrefix, suffixNumber)
            }
        }
    }
}
