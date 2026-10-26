package org.schabi.newpipe.recommendations

import org.schabi.newpipe.settings.tabs.Tab

/** Home-tab sync must not remove the device-local recommendation destination. */
object RecommendationHomeTabs {
    fun preserve(local: List<Tab>, incoming: List<Tab>): List<Tab> {
        val index = local.indexOfFirst { it is Tab.RecommendationsTab }
        if (index < 0) return incoming
        return incoming.filterNot { it is Tab.RecommendationsTab }.toMutableList().apply {
            add(index.coerceAtMost(size), local[index])
        }
    }
}
