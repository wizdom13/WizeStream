package org.schabi.newpipe.local.subscription

/** Uses channel keys instead of row positions so filtering and live updates retain selection. */
internal class SubscriptionSortModel {
    var entries = emptyList<SubscriptionLayout.Entry>()
        private set
    val selected = linkedSetOf<String>()

    fun replace(saved: List<SubscriptionLayout.Entry>, channelKeys: List<String>): Boolean {
        val updated = SubscriptionLayout.reconcile(saved, channelKeys)
        val selectionChanged = selected.retainAll(channelKeys.toSet())
        val changed = updated != entries || selectionChanged
        entries = updated
        return changed
    }

    fun toggle(key: String) {
        if (entries.none { it.key == key && it.separator == null }) return
        if (!selected.remove(key)) selected.add(key)
    }

    fun selectVisible(keys: Set<String>) {
        val channels = entries.filter { it.separator == null && it.key in keys }.map { it.key }
        if (channels.isNotEmpty() && selected.containsAll(channels)) {
            selected.removeAll(channels.toSet())
        } else {
            selected.addAll(channels)
        }
    }

    fun move(fromKey: String, toKey: String): Boolean {
        val from = entries.indexOfFirst { it.key == fromKey }
        val to = entries.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return false
        entries = entries.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    fun moveSelected(separatorKey: String): Boolean {
        if (entries.none { it.key == separatorKey && it.separator != null }) return false
        val moving = entries.filter { it.separator == null && it.key in selected }
        if (moving.isEmpty()) return false
        val remaining = entries.filterNot { it.separator == null && it.key in selected }.toMutableList()
        remaining.addAll(remaining.indexOfFirst { it.key == separatorKey } + 1, moving)
        entries = remaining
        selected.clear()
        return true
    }

    fun putSeparator(key: String, name: String) {
        require(name.isNotBlank())
        val updated = SubscriptionLayout.Entry(key, name.trim())
        val index = entries.indexOfFirst { it.key == key }
        entries = entries.toMutableList().apply {
            if (index < 0) add(updated) else set(index, updated)
        }
    }

    fun removeSeparator(key: String) {
        entries = entries.filterNot { it.key == key && it.separator != null }
    }
}
