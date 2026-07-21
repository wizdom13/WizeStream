package org.schabi.newpipe.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.update.WizeStreamUpdateRepository.VersionComparison

class WizeStreamUpdateRepositoryTest {
    @Test
    fun compareInstalledToLatestDetectsNewerMaterialRelease() {
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("0.28.7-m3", "0.28.8-m1")
        )
    }

    @Test
    fun compareInstalledToLatestTreatsMatchingVPrefixedTagAsCurrent() {
        assertEquals(
            VersionComparison.SAME_OR_OLDER,
            WizeStreamUpdateRepository.compareInstalledToLatest("0.28.7-m3", "v0.28.7-m3")
        )
    }

    @Test
    fun compareInstalledToLatestFailsSafelyForUnknownInstalledVersion() {
        assertEquals(
            VersionComparison.UNKNOWN,
            WizeStreamUpdateRepository.compareInstalledToLatest("material-dev", "v0.28.7-m3")
        )
    }

    @Test
    fun selectLatestCandidateReleaseIgnoresDrafts() {
        val draft = release("0.29.0-m1", "2026-02-01T00:00:00Z", draft = true)
        val published = release("0.28.8-m1", "2026-01-01T00:00:00Z")

        assertEquals(
            published,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(draft, published))
        )
    }

    @Test
    fun selectLatestCandidateReleaseIncludesPrereleases() {
        val stable = release("0.28.7-m3", "2026-01-01T00:00:00Z")
        val prerelease = release("0.28.8-m1", "2026-01-02T00:00:00Z", prerelease = true)

        assertEquals(
            prerelease,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(stable, prerelease))
        )
    }

    @Test
    fun selectLatestCandidateReleaseUsesNewestPublishedNonDraftRelease() {
        val older = release("0.29.0-m1", "2026-01-01T00:00:00Z")
        val newer = release("0.28.8-m1", "2026-01-02T00:00:00Z")

        assertEquals(
            newer,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(older, newer))
        )
    }

    @Test
    fun selectLatestCandidateReleaseHandlesEmptyListSafely() {
        assertNull(WizeStreamUpdateRepository.selectLatestCandidateRelease(emptyList()))
    }

    @Test
    fun selectLatestCandidateReleaseCanSelectPrereleaseNewerThanInstalled() {
        val prerelease = release("0.28.8-m1", "2026-01-02T00:00:00Z", prerelease = true)

        assertEquals(
            prerelease,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(prerelease))
        )
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("0.28.7-m3", prerelease.version)
        )
    }

    @Test
    fun parseReleasesReadsGitHubSha256Digest() {
        val releases = WizeStreamUpdateRepository.parseReleases(
            releaseJson("sha256:${"ab".repeat(32)}")
        )

        assertEquals("ab".repeat(32), releases.single().apkSha256)
        assertEquals(12_345L, releases.single().apkSize)
    }

    @Test
    fun parseReleasesRejectsUnsupportedOrMalformedDigest() {
        val unsupported = WizeStreamUpdateRepository.parseReleases(
            releaseJson("sha512:${"ab".repeat(64)}")
        )
        val malformed = WizeStreamUpdateRepository.parseReleases(
            releaseJson("sha256:not-a-checksum")
        )

        assertNull(unsupported.single().apkSha256)
        assertNull(malformed.single().apkSha256)
    }

    private fun releaseJson(digest: String): String {
        return """
            [
              {
                "tag_name": "v0.28.8-m12",
                "name": "WizeStream 0.28.8-m12",
                "html_url": "https://github.com/wizdom13/WizeStream/releases/tag/v0.28.8-m12",
                "body": "Release notes",
                "published_at": "2026-07-21T00:00:00Z",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "WizeStream_v0.28.8-m12.apk",
                    "browser_download_url": "https://example.invalid/WizeStream.apk",
                    "size": 12345,
                    "digest": "$digest"
                  }
                ]
              }
            ]
        """.trimIndent()
    }

    private fun release(
        version: String,
        publishedAt: String,
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = WizeStreamUpdateRepository.Release(
        version = version,
        title = version,
        htmlUrl = "https://github.com/wizdom13/WizeStream/releases/tag/$version",
        body = "Release notes",
        publishedAt = publishedAt,
        draft = draft,
        prerelease = prerelease,
        apkUrl = null
    )
}
