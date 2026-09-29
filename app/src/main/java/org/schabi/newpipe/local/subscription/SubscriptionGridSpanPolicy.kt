package org.schabi.newpipe.local.subscription

object SubscriptionGridSpanPolicy {
    @JvmStatic
    fun resolve(
        configuredColumns: Int,
        autoColumns: Int
    ): Int = if (configuredColumns == SubscriptionGridColumns.AUTO) {
        autoColumns
    } else {
        configuredColumns
    }
}
