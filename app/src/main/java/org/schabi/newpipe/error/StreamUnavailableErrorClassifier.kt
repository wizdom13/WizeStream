package org.schabi.newpipe.error

import java.util.Locale
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException

object StreamUnavailableErrorClassifier {
    private val unavailableMarkers = listOf(
        "removed",
        "taken down",
        "takedown",
        "copyright",
        "community guidelines",
        "no longer available",
        "video unavailable",
        "content unavailable",
        "deleted by",
        "removed by"
    )

    @JvmStatic
    fun isKnownUnavailable(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is ContentNotAvailableException &&
            ErrorInfo.isContentSurelyNotAvailable(throwable)
        ) {
            return true
        }
        if (throwable !is ExtractionException) return false

        return generateSequence(throwable as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .map { it.lowercase(Locale.ROOT) }
            .any { message -> unavailableMarkers.any(message::contains) }
    }
}
