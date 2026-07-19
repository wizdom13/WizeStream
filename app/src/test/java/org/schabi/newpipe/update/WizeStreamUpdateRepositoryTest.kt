@file:Suppress("ktlint:standard:filename", "ktlint:standard:class-naming")

package org.schabi.newpipe.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.update.wizestreamUpdateRepository.VersionComparison

class wizestreamUpdateRepositoryTest {
    @Test
    fun compareInstalledToLatestDetectsNewerMaterialRelease() {
        assertEquals(
            VersionComparison.NEWER,
            wizestreamUpdateRepository.compareInstalledToLatest("0.28.7-m3", "0.28.8-m1")
        )
    }

    @Test
    fun compareInstalledToLatestTreatsMatchingVPrefixedTagAsCurrent() {
        assertEquals(
            VersionComparison.SAME_OR_OLDER,
            wizestreamUpdateRepository.compareInstalledToLatest("0.28.7-m3", "v0.28.7-m3")
        )
    }

    @Test
    fun compareInstalledToLatestFailsSafelyForUnknownInstalledVersion() {
        assertEquals(
            VersionComparison.UNKNOWN,
            wizestreamUpdateRepository.compareInstalledToLatest("material-dev", "v0.28.7-m3")
        )
    }

    @Test
    fun selectLatestCandidateReleaseIgnoresDrafts() {
        val draft = release("0.29.0-m1", "2026-02-01T00:00:00Z", draft = true)
        val published = release("0.28.8-m1", "2026-01-01T00:00:00Z")

        assertEquals(
            published,
            wizestreamUpdateRepository.selectLatestCandidateRelease(listOf(draft, published))
        )
    }

    @Test
    fun selectLatestCandidateReleaseIncludesPrereleases() {
        val stable = release("0.28.7-m3", "2026-01-01T00:00:00Z")
        val prerelease = release("0.28.8-m1", "2026-01-02T00:00:00Z", prerelease = true)

        assertEquals(
            prerelease,
            wizestreamUpdateRepository.selectLatestCandidateRelease(listOf(stable, prerelease))
        )
    }

    @Test
    fun selectLatestCandidateReleaseUsesNewestPublishedNonDraftRelease() {
        val older = release("0.29.0-m1", "2026-01-01T00:00:00Z")
        val newer = release("0.28.8-m1", "2026-01-02T00:00:00Z")

        assertEquals(
            newer,
            wizestreamUpdateRepository.selectLatestCandidateRelease(listOf(older, newer))
        )
    }

    @Test
    fun selectLatestCandidateReleaseHandlesEmptyListSafely() {
        assertNull(wizestreamUpdateRepository.selectLatestCandidateRelease(emptyList()))
    }

    @Test
    fun selectLatestCandidateReleaseCanSelectPrereleaseNewerThanInstalled() {
        val prerelease = release("0.28.8-m1", "2026-01-02T00:00:00Z", prerelease = true)

        assertEquals(
            prerelease,
            wizestreamUpdateRepository.selectLatestCandidateRelease(listOf(prerelease))
        )
        assertEquals(
            VersionComparison.NEWER,
            wizestreamUpdateRepository.compareInstalledToLatest("0.28.7-m3", prerelease.version)
        )
    }

    private fun release(
        version: String,
        publishedAt: String,
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = wizestreamUpdateRepository.Release(
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
