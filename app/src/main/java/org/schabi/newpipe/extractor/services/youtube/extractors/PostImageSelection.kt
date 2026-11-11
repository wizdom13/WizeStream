/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import org.schabi.newpipe.extractor.Image

/** A renderer's thumbnails are resolutions of one photo, not separate post attachments. */
internal object PostImageSelection {
    @JvmStatic
    fun singlePhoto(variants: List<Image>): List<Image> = variants.maxWithOrNull(
        compareBy<Image> { it.width.coerceAtLeast(0).toLong() * it.height.coerceAtLeast(0) }
            .thenBy { maxOf(it.width, it.height) }
            .thenBy { -it.estimatedResolutionLevel.ordinal }
    )?.let(::listOf).orEmpty()
}
