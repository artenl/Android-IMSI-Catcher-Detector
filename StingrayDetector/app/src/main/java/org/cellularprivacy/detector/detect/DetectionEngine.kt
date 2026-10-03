package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.collect.OperatorFacts
import org.cellularprivacy.detector.detect.heuristics.EmptyNeighborListHeuristic
import org.cellularprivacy.detector.detect.heuristics.OperatorMismatchHeuristic
import org.cellularprivacy.detector.detect.heuristics.RatDowngradeHeuristic
import org.cellularprivacy.detector.detect.heuristics.AnfrMismatchHeuristic
import org.cellularprivacy.detector.detect.heuristics.SignalAnomalyHeuristic
import org.cellularprivacy.detector.detect.heuristics.SuspiciousPhysicalParamsHeuristic
import org.cellularprivacy.detector.detect.heuristics.UnknownStrongCellHeuristic
import org.cellularprivacy.detector.model.CellSnapshot

/**
 * Orchestrates a batch of [CellSnapshot] through every [Heuristic] and feeds
 * the results into the [ScoreAggregator].
 *
 * History is updated AFTER heuristics run, so a heuristic sees the state as it
 * was before the current batch (e.g. "have I seen this cell before now?").
 */
class DetectionEngine(
    private val heuristics: List<Heuristic> = defaultHeuristics(),
    private val history: CellHistory = CellHistory(),
    private val aggregator: ScoreAggregator = ScoreAggregator()
) {

    fun process(
        batch: List<CellSnapshot>,
        operator: OperatorFacts,
        deviceMoving: Boolean,
        userLat: Double? = null,
        userLon: Double? = null,
        anfr: List<AnfrRef> = emptyList()
    ): Assessment {
        val serving = pickServing(batch)
        val ctx = DetectionContext(
            serving = serving,
            visible = batch,
            history = history,
            operator = operator,
            deviceMoving = deviceMoving,
            userLat = userLat,
            userLon = userLon,
            anfr = anfr
        )

        // Use observation time, not wall-clock, so scoring/decay is driven by the
        // data and stays deterministic (and testable with replayed traces).
        val now = batch.maxOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
        val results = heuristics.mapNotNull { it.evaluate(ctx) }
            .map { it.copy(timestampMs = now) }
        val assessment = aggregator.apply(results, now)

        history.record(batch, serving)
        return assessment
    }

    /** Serving = first registered cell; fall back to strongest signal. */
    private fun pickServing(batch: List<CellSnapshot>): CellSnapshot? =
        batch.firstOrNull { it.registered }
            ?: batch.maxByOrNull { it.dbm ?: Int.MIN_VALUE }

    fun reset() {
        history.clear()
        aggregator.reset()
    }

    companion object {
        fun defaultHeuristics(): List<Heuristic> = listOf(
            RatDowngradeHeuristic(),
            UnknownStrongCellHeuristic(),
            OperatorMismatchHeuristic(),
            EmptyNeighborListHeuristic(),
            SuspiciousPhysicalParamsHeuristic(),
            SignalAnomalyHeuristic(),
            AnfrMismatchHeuristic()
        )
    }
}
