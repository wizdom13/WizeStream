/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download.offline

internal data class ManagedDownload(val id: String, val channel: String, val created: Long, val bytes: Long, val watched: Boolean, val keep: Boolean)
internal data class RetentionRules(val enabled: Boolean = false, val latest: Int = 0, val budgetBytes: Long = 0, val deleteWatched: Boolean = false)

internal object OfflineRetentionPolicy {
    fun candidates(items: List<ManagedDownload>, rules: RetentionRules): Set<String> {
        if (!rules.enabled) return emptySet()
        val remove = linkedSetOf<String>()
        if (rules.deleteWatched) items.filter { it.watched && !it.keep }.forEach { remove.add(it.id) }
        if (rules.latest > 0) {
            items.groupBy { it.channel }.values.forEach { group ->
                group.sortedWith(compareByDescending<ManagedDownload> { it.created }.thenBy { it.id })
                    .drop(rules.latest).filterNot { it.keep }.forEach { remove.add(it.id) }
            }
        }
        if (rules.budgetBytes > 0) {
            var remaining = items.filterNot { it.id in remove }.sumOf { it.bytes.coerceAtLeast(0) }
            for (item in items.filterNot { it.keep || it.id in remove }.sortedWith(compareBy<ManagedDownload> { it.created }.thenBy { it.id })) {
                if (remaining <= rules.budgetBytes) break
                remove.add(item.id)
                remaining -= item.bytes.coerceAtLeast(0)
            }
        }
        return remove
    }
}
