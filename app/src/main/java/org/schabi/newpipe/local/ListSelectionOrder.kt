package org.schabi.newpipe.local

/** Reorders item occurrences while retaining independent separator rows. */
internal object ListSelectionOrder {
    data class Result(val itemIndices: List<Int>, val labels: Map<Int, String>)
    private data class Row(val item: Int = -1, val label: String? = null)

    fun move(itemCount: Int, labels: Map<Int, String>, selected: Set<Int>, boundary: Int): Result {
        require(selected.all { it in 0 until itemCount })
        require(boundary in 0..(itemCount + labels.size))
        var item = 0
        val rows = (0 until itemCount + labels.size).map { row ->
            labels[row]?.let { Row(label = it) } ?: Row(item = item++)
        }
        require(item == itemCount)
        val moving = rows.filter { it.item in selected }
        val destination = rows.take(boundary).count { it.item !in selected }
        val reordered = rows.filterNot { it.item in selected }.toMutableList()
        reordered.addAll(destination, moving)
        return Result(
            reordered.filter { it.item >= 0 }.map { it.item },
            reordered.mapIndexedNotNull { index, row -> row.label?.let { index to it } }.toMap()
        )
    }
}
