/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.bandcamp.extractors

import java.io.IOException
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

internal object BandcampSearchRequests {
    @JvmStatic
    @Throws(IOException::class, ReCaptchaException::class)
    fun fetch(downloader: Downloader, url: String): String {
        // Bandcamp otherwise serves its browser challenge instead of search results.
        // This anonymous compatibility cookie also matches NewPipeExtractor's search requests.
        return downloader.get(url, mapOf("Cookie" to listOf("identity"))).responseBody()
    }
}
