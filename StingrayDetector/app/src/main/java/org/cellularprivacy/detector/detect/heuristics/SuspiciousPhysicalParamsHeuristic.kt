package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult

/**
 * Fires on physical-layer parameters typical of cheap fake BTS kit:
 *  - very narrow LTE bandwidth (1.4 or 5 MHz), the srsRAN/OpenBTS default,
 *  - Timing Advance ~0 combined with a saturated signal, i.e. an emitter only
 *    tens of metres away.
 */
class SuspiciousPhysicalParamsHeuristic : Heuristic {
    override val id = "suspicious_physical"

    private val narrowBandwidthsKhz = setOf(1400, 5000)

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null

        val reasons = mutableListOf<String>()

        serving.bandwidthKhz?.let { bw ->
            if (bw in narrowBandwidthsKhz) reasons += "bande passante étroite ${bw / 1000.0} MHz"
        }

        val ta = serving.timingAdvance
        val dbm = serving.dbm
        if (ta != null && ta <= HeuristicWeights.CLOSE_TIMING_ADVANCE &&
            dbm != null && dbm >= HeuristicWeights.SATURATED_DBM
        ) {
            reasons += "émetteur très proche (TA=$ta, $dbm dBm)"
        }

        if (reasons.isEmpty()) return null

        return HeuristicResult(
            id = id,
            title = "Paramètres physiques suspects",
            detail = reasons.joinToString("; "),
            weight = HeuristicWeights.SUSPICIOUS_PHYSICAL
        )
    }
}
