package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult
import kotlin.math.abs

/**
 * Fires when the serving cell's signal departs sharply from its own learned
 * baseline while the device is stationary. Finishes what the old
 * SignalStrengthTracker.isMysterious() intended but never wired up.
 */
class SignalAnomalyHeuristic : Heuristic {
    override val id = "signal_anomaly"

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null
        if (ctx.deviceMoving) return null
        val dbm = serving.dbm ?: return null
        val baseline = ctx.history.baselineDbm(serving) ?: return null

        val delta = abs(dbm - baseline)
        if (delta < HeuristicWeights.SIGNAL_ANOMALY_DELTA) return null

        // Confidence scales with how far past the threshold we are, capped at 1.
        val confidence = (delta.toDouble() / (HeuristicWeights.SIGNAL_ANOMALY_DELTA * 2))
            .coerceIn(0.0, 1.0)

        return HeuristicResult(
            id = id,
            title = "Anomalie de signal",
            detail = "Signal $dbm dBm vs référence ${baseline.toInt()} dBm (écart ${delta}).",
            weight = HeuristicWeights.SIGNAL_ANOMALY,
            confidence = confidence
        )
    }
}
