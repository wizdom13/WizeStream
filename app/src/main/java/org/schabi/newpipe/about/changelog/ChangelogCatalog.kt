package org.schabi.newpipe.about.changelog

import org.jsoup.Jsoup

internal data class ChangelogRelease(val version: String, val code: Int, val html: String)
internal data class ChangelogNotice(val version: String, val code: Int, val html: String)

internal class ChangelogCatalog(private val releases: List<ChangelogRelease>) {
    fun notice(versionName: String, lastSeenCode: Int): ChangelogNotice? {
        // Nightly APKs override the Android version code; use the release identified by the name.
        val current = releases.firstOrNull { versionName == it.version || versionName.startsWith("${it.version}-") } ?: return null
        if (lastSeenCode >= current.code) return null
        val changes = if (lastSeenCode <= 0) {
            listOf(current)
        } else {
            releases.filter { it.code > lastSeenCode && it.code <= current.code }
        }
        return ChangelogNotice(current.version, current.code, changes.joinToString("\n") { it.html })
    }

    companion object {
        fun parse(html: String): ChangelogCatalog {
            val document = Jsoup.parse(html)
            val releases = document.select("section[data-version-code]").mapNotNull { section ->
                val code = section.attr("data-version-code").toIntOrNull() ?: return@mapNotNull null
                val version = section.attr("data-version")
                val notes = section.selectFirst(".release-notes") ?: return@mapNotNull null
                if (version.isBlank() || notes.text().isBlank()) return@mapNotNull null
                ChangelogRelease(version, code, section.html())
            }.sortedByDescending { it.code }
            return ChangelogCatalog(releases)
        }
    }
}
