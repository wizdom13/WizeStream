package org.schabi.newpipe.util.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image

class RemotePlaylistArtworkResolverTest {
    @Test
    fun playlistArtworkWinsOverAllFallbacks() {
        assertEquals(
            "https://img.example/playlist.jpg",
            resolve(
                playlist = listOf(image("https://img.example/playlist.jpg")),
                streams = listOf(listOf(image("https://img.example/track.jpg"))),
                uploader = listOf(image("https://img.example/avatar.jpg")),
                existing = "https://img.example/existing.jpg"
            )
        )
    }

    @Test
    fun firstUsableStreamArtworkRepairsMissingBookmarkCover() {
        assertEquals(
            "https://img.example/track-2.jpg",
            resolve(
                playlist = emptyList(),
                streams = listOf(
                    listOf(image("")),
                    listOf(image("https://img.example/track-2.jpg")),
                    listOf(image("https://img.example/track-3.jpg"))
                ),
                uploader = listOf(image("https://img.example/avatar.jpg")),
                existing = null
            )
        )
    }

    @Test
    fun existingStoredCoverIsPreservedBeforeAvatarFallback() {
        assertEquals(
            "https://img.example/existing.jpg",
            resolve(
                playlist = emptyList(),
                streams = emptyList(),
                uploader = listOf(image("https://img.example/avatar.jpg")),
                existing = "https://img.example/existing.jpg"
            )
        )
    }

    @Test
    fun uploaderAvatarIsUsedForNewBookmarkWhenNoArtworkExists() {
        assertEquals(
            "https://img.example/avatar.jpg",
            resolve(
                playlist = emptyList(),
                streams = emptyList(),
                uploader = listOf(image("https://img.example/avatar.jpg")),
                existing = null
            )
        )
    }

    @Test
    fun newStreamArtworkReplacesPreviouslyStoredFallback() {
        assertEquals(
            "https://img.example/track.jpg",
            resolve(
                playlist = emptyList(),
                streams = listOf(listOf(image("https://img.example/track.jpg"))),
                uploader = listOf(image("https://img.example/avatar.jpg")),
                existing = "https://img.example/existing.jpg"
            )
        )
    }

    @Test
    fun protocolRelativeArtworkIsNormalizedBeforePersistence() {
        assertEquals(
            "https://i.ytimg.com/vi/example/hqdefault.jpg",
            resolve(
                playlist = listOf(image("//i.ytimg.com/vi/example/hqdefault.jpg")),
                streams = emptyList(),
                uploader = emptyList(),
                existing = null
            )
        )
    }

    private fun resolve(
        playlist: List<Image>,
        streams: List<List<Image>>,
        uploader: List<Image>,
        existing: String?
    ): String? {
        return RemotePlaylistArtworkResolver.resolve(
            playlistImages = playlist,
            streamImages = streams,
            uploaderImages = uploader,
            existingThumbnailUrl = existing
        )
    }

    private fun image(url: String): Image {
        return Image(
            url,
            Image.HEIGHT_UNKNOWN,
            Image.WIDTH_UNKNOWN,
            Image.ResolutionLevel.UNKNOWN
        )
    }
}
