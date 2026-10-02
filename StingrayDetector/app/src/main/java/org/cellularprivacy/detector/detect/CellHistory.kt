package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.model.CellSnapshot
import org.cellularprivacy.detector.model.Rat

/**
 * Rolling, in-memory view of what has been seen recently. The engine feeds it
 * each batch; heuristics query it. A persistent copy lives in Room, but
 * heuristics run against this fast cache.
 */
class CellHistory(private val maxServingHistory: Int = 20) {

    private val seenKeys = HashSet<String>()
    private val seenPhysical = HashSet<String>() // "rat:pci:arfcn"
    private val servingHistory = ArrayDeque<CellSnapshot>()

    /** key -> exponential moving average of dbm, for the signal baseline. */
    private val dbmEma = HashMap<String, Double>()
    private val emaAlpha = 0.2

    fun hasSeen(cell: CellSnapshot): Boolean = seenKeys.contains(cell.key)

    fun hasSeenPhysical(cell: CellSnapshot): Boolean {
        val k = physicalKey(cell) ?: return true // unknown -> don't flag
        return seenPhysical.contains(k)
    }

    /** Previous serving cell (one before the current one), or null. */
    fun previousServing(): CellSnapshot? =
        if (servingHistory.size >= 2) servingHistory[servingHistory.size - 2] else null

    fun lastServingOfGeneration(minGeneration: Int, withinMs: Long, now: Long): CellSnapshot? =
        servingHistory.lastOrNull {
            it.rat.generation >= minGeneration && now - it.timestampMs <= withinMs
        }

    /** Baseline dbm for a cell, or null if we have no history yet. */
    fun baselineDbm(cell: CellSnapshot): Double? = dbmEma[cell.key]

    fun record(batch: List<CellSnapshot>, serving: CellSnapshot?) {
        for (c in batch) {
            seenKeys.add(c.key)
            physicalKey(c)?.let { seenPhysical.add(it) }
            c.dbm?.let { dbm ->
                val prev = dbmEma[c.key]
                dbmEma[c.key] = if (prev == null) dbm.toDouble()
                else emaAlpha * dbm + (1 - emaAlpha) * prev
            }
        }
        if (serving != null) {
            servingHistory.addLast(serving)
            while (servingHistory.size > maxServingHistory) servingHistory.removeFirst()
        }
    }

    private fun physicalKey(c: CellSnapshot): String? {
        val pci = c.physicalId ?: return null
        val arfcn = c.arfcn ?: return null
        return "${c.rat.name}:$pci:$arfcn"
    }

    fun clear() {
        seenKeys.clear(); seenPhysical.clear(); servingHistory.clear(); dbmEma.clear()
    }
}
