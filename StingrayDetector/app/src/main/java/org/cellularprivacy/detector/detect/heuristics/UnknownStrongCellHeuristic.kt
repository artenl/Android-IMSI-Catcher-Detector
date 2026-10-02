package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult

/**
 * Fires when the serving cell has never been seen before AND presents an
 * unusually strong signal while the device is stationary. A genuine new cell
 * normally appears when moving; a never-seen, saturated cell at a fixed spot
 * is a hallmark of a nearby rogue BTS.
 */
class UnknownStrongCellHeuristic : Heuristic {
    override val id = "unknown_strong_cell"

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null
        if (ctx.deviceMoving) return null
        if (!serving.isIdentified) return null
        if (ctx.history.hasSeen(serving)) return null

        val dbm = serving.dbm ?: return null
        if (dbm < HeuristicWeights.SATURATED_DBM) return null

        return HeuristicResult(
            id = id,
            title = "Cellule inconnue au signal saturé",
            detail = "CID ${serving.cellId} jamais observé ici, signal $dbm dBm à l'arrêt.",
            weight = HeuristicWeights.UNKNOWN_STRONG_CELL
        )
    }
}
