package org.schabi.newpipe.local.subscription

import java.util.Locale
import org.schabi.newpipe.database.subscription.SubscriptionEntity

object SubscriptionSearch {
    fun matches(query: String, subscription: SubscriptionEntity): Boolean {
        val text = query.trim().removePrefix("@").lowercase(Locale.ROOT)
        if (text.isEmpty()) return true
        return listOf(subscription.name, subscription.handle).any { it?.lowercase(Locale.ROOT)?.contains(text) == true }
    }
}
