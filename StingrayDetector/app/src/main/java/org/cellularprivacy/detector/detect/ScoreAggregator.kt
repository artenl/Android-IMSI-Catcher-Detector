package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.model.HeuristicResult
import org.cellularprivacy.detector.model.ThreatLevel

/**
 * Combines live heuristic results into a single score and [ThreatLevel].
 *
 * Results decay linearly over [decayMs] so a positive clears once the user
 * moves away and the condition stops recurring. This addresses the old
 * CellTracker TODO: "We need a timer to reverse a positive detection once we're
 * out and away from the fake BTS cell."
 */
class ScoreAggregator(private val decayMs: Long = 120_000L) {

    private val active = LinkedHashMap<String, HeuristicResult>()

    /** Add/refresh results from the latest evaluation and return the level. */
    fun apply(results: List<HeuristicResult>, now: Long = System.currentTimeMillis()): Assessment {
        for (r in results) active[r.id] = r
        // Drop fully decayed entries.
        active.entries.removeAll { now - it.value.timestampMs > decayMs }

        var score = 0
        val contributing = ArrayList<HeuristicResult>()
        for (r in active.values) {
            val age = now - r.timestampMs
            val decay = (1.0 - age.toDouble() / decayMs).coerceIn(0.0, 1.0)
            val decayed = (r.score * decay).toInt()
            if (decayed > 0) {
                score += decayed
                contributing += r
            }
        }
        return Assessment(ThreatLevel.fromScore(score), score, contributing)
    }

    fun reset() = active.clear()
}

data class Assessment(
    val level: ThreatLevel,
    val score: Int,
    val contributing: List<HeuristicResult>
)
