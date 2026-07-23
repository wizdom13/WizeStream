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
        val apkSize: Long? = null,
        val apkSha256: String? = null
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
            .mapNotNull { release ->
                SemanticVersion.parse(release.version)?.let { release to it }
            }
            .maxWithOrNull(
                compareBy<Pair<Release, SemanticVersion>> { it.second }
                    .thenBy { parsePublishedAt(it.first.publishedAt) }
            )
            ?.first
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
        return BuildConfig.VERSION_NAME
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
            apkSize = apkAsset?.getLong("size", -1)?.takeIf { it >= 0 },
            apkSha256 = UpdateChecksum.fromGitHubDigest(
                apkAsset?.getString("digest", null)
            )
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
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: List<String>,
        val legacyRevision: Int? = null
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            compareValuesBy(
                this,
                other,
                SemanticVersion::major,
                SemanticVersion::minor,
                SemanticVersion::patch
            ).takeIf { it != 0 }?.let { return it }

            if (legacyRevision != null || other.legacyRevision != null) {
                return when {
                    legacyRevision == null -> -1
                    other.legacyRevision == null -> 1
                    else -> legacyRevision.compareTo(other.legacyRevision)
                }
            }

            if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
                return when {
                    prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
                    prerelease.isEmpty() -> 1
                    else -> -1
                }
            }

            val maxSize = maxOf(prerelease.size, other.prerelease.size)
            for (index in 0 until maxSize) {
                val current = prerelease.getOrNull(index) ?: return -1
                val otherValue = other.prerelease.getOrNull(index) ?: return 1
                if (current == otherValue) {
                    continue
                }

                val currentNumber = current.toIntOrNull()
                val otherNumber = otherValue.toIntOrNull()
                return when {
                    currentNumber != null && otherNumber != null ->
                        currentNumber.compareTo(otherNumber)

                    currentNumber != null -> -1

                    otherNumber != null -> 1

                    else -> current.compareTo(otherValue)
                }
            }
            return 0
        }

        companion object {
            private val LEGACY_VERSION_REGEX =
                Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)-m(\\d+)$")
            private val VERSION_REGEX = Regex(
                "^v?(\\d+)\\.(\\d+)\\.(\\d+)" +
                    "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
                    "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
            )

            fun parse(raw: String): SemanticVersion? {
                LEGACY_VERSION_REGEX.matchEntire(raw.trim())?.let { match ->
                    val major = parseNumber(match.groupValues[1]) ?: return null
                    val minor = parseNumber(match.groupValues[2]) ?: return null
                    val patch = parseNumber(match.groupValues[3]) ?: return null
                    val legacyRevision = parseNumber(match.groupValues[4]) ?: return null
                    return SemanticVersion(
                        major = major,
                        minor = minor,
                        patch = patch,
                        prerelease = emptyList(),
                        legacyRevision = legacyRevision
                    )
                }

                val match = VERSION_REGEX.matchEntire(raw.trim()) ?: return null
                val major = parseNumber(match.groupValues[1]) ?: return null
                val minor = parseNumber(match.groupValues[2]) ?: return null
                val patch = parseNumber(match.groupValues[3]) ?: return null
                val prerelease = match.groupValues[4]
                    .takeIf { it.isNotBlank() }
                    ?.split('.')
                    .orEmpty()
                val hasInvalidNumericIdentifier = prerelease.any { identifier ->
                    identifier.all(Char::isDigit) &&
                        identifier.length > 1 &&
                        identifier.startsWith('0')
                }
                if (hasInvalidNumericIdentifier) {
                    return null
                }
                return SemanticVersion(major, minor, patch, prerelease)
            }

            private fun parseNumber(value: String): Int? {
                if (value.length > 1 && value.startsWith('0')) {
                    return null
                }
                return value.toIntOrNull()
            }
        }
    }
}
