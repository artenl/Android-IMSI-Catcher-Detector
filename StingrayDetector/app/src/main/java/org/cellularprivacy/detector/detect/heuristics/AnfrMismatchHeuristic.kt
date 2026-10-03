package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.FrOperators
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult
import org.cellularprivacy.detector.util.Geo

/**
 * Cross-checks the serving cell against official ANFR sites: if you are served by
 * an operator but no official site of that operator exists near your position,
 * that is suspicious (you cannot be legitimately served by an operator that has
 * no antenna in range).
 *
 * Guards against false positives:
 *  - needs a GPS fix and a loaded ANFR data pack,
 *  - needs at least one official site nearby (else the area simply isn't in the
 *    downloaded pack, so we cannot judge),
 *  - only applies in France (MCC 208) with a known operator mapping.
 */
class AnfrMismatchHeuristic : Heuristic {
    override val id = "anfr_mismatch"

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null
        val lat = ctx.userLat ?: return null
        val lon = ctx.userLon ?: return null
        if (ctx.anfr.isEmpty()) return null

        val operator = FrOperators.operatorForMnc(serving.mcc, serving.mnc) ?: return null

        val radius = HeuristicWeights.ANFR_RADIUS_M
        val near = ctx.anfr.filter { Geo.haversine(lat, lon, it.lat, it.lon) <= radius }
        if (near.isEmpty()) return null // area not covered by the data pack

        val hasOperatorSite = near.any { it.operators.contains(operator) }
        if (hasOperatorSite) return null

        return HeuristicResult(
            id = id,
            title = "Opérateur sans antenne officielle proche",
            detail = "Cellule $operator, mais aucun site ANFR $operator dans " +
                "${radius.toInt()} m (${near.size} sites officiels autour).",
            weight = HeuristicWeights.ANFR_MISMATCH
        )
    }
}
