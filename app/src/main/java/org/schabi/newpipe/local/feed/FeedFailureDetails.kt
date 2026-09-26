package org.schabi.newpipe.local.feed

import org.schabi.newpipe.local.feed.service.FeedLoadService

internal object FeedFailureDetails {
    @JvmStatic
    fun subscriptionIds(errors: List<Throwable>): List<Long> {
        return errors.asSequence()
            .filterIsInstance<FeedLoadService.RequestException>()
            .map { it.subscriptionId }
            .distinct()
            .toList()
    }
}
