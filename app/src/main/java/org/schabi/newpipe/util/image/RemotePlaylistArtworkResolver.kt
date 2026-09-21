/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util.image

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

/**
 * Resolves durable artwork for remote playlist bookmarks.
 *
 * Search results can expose artwork even when YouTube's full playlist header does not. Bookmark
 * metadata therefore falls back to the first usable stream thumbnail before using the uploader
 * avatar. During refresh, an existing stored cover is preserved before degrading to an avatar.
 */
object RemotePlaylistArtworkResolver {
    @JvmStatic
    @JvmOverloads
    fun resolve(
        playlistInfo: PlaylistInfo,
        existingThumbnailUrl: String? = null
    ): String? {
        val streamImages = playlistInfo.relatedItems
            .map { ExtractorImageCompat.thumbnailImages(it) }

        return resolve(
            playlistImages = ExtractorImageCompat.thumbnailImages(playlistInfo),
            streamImages = streamImages,
            uploaderImages = ExtractorImageCompat.uploaderAvatarImages(playlistInfo),
            existingThumbnailUrl = existingThumbnailUrl
        )
    }

    internal fun resolve(
        playlistImages: List<Image>,
        streamImages: List<List<Image>>,
        uploaderImages: List<Image>,
        existingThumbnailUrl: String?
    ): String? {
        chooseUsable(playlistImages)?.let { return it }

        streamImages.forEach { images ->
            chooseUsable(images)?.let { return it }
        }

        // Do not downgrade an already-good bookmark to an uploader avatar just because a later
        // playlist extraction temporarily omitted its artwork.
        normalizeUrl(existingThumbnailUrl)?.let { return it }

        return chooseUsable(uploaderImages)
    }

    private fun chooseUsable(images: List<Image>): String? {
        val usableImages = images.filter { normalizeUrl(it.url) != null }
        if (usableImages.isEmpty()) {
            return null
        }
        return normalizeUrl(ImageStrategy.imageListToDbUrl(usableImages))
    }

    private fun normalizeUrl(url: String?): String? {
        val normalized = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return when {
            normalized.startsWith("//") -> "https:$normalized"
            normalized.startsWith("https://", ignoreCase = true) -> normalized
            normalized.startsWith("http://", ignoreCase = true) -> normalized
            else -> null
        }
    }
}
