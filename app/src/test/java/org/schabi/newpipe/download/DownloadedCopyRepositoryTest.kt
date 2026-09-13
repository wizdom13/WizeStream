package org.schabi.newpipe.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedCopyRepositoryTest {
    @Test
    fun matchesYouTubeAliasesAndIgnoresPlaybackPosition() {
        assertTrue(DownloadedCopyRepository.sameSource(0, "https://youtu.be/dQw4w9WgXcQ?t=20", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(DownloadedCopyRepository.sameSource(0, "https://www.youtube.com/shorts/dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun neverSubstitutesPartialExportsForCompleteStreams() {
        assertFalse(DownloadedCopyRepository.sameSource(0, "https://youtu.be/dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ#wizestream-segments=1000:2000"))
    }

    @Test
    fun rejectsDifferentVideosAndLookalikeHosts() {
        assertFalse(DownloadedCopyRepository.sameSource(0, "https://youtu.be/dQw4w9WgXcQ", "https://www.youtube.com/watch?v=abcdefghijk"))
        assertFalse(DownloadedCopyRepository.sameSource(0, "https://youtu.be/dQw4w9WgXcQ", "https://youtube.example/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun keepsNonYouTubeQueryIdentitiesDistinct() {
        assertFalse(DownloadedCopyRepository.sameSource(99, "https://example.com/video?id=1", "https://example.com/video?id=2"))
        assertTrue(DownloadedCopyRepository.sameSource(99, "https://example.com/video?id=1", "https://example.com/video?id=1"))
    }
}
