package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult

/**
 * Fires when an LTE serving cell reports no neighbours at all. Real macro cells
 * almost always have neighbours; a lone cell with an empty neighbour list is a
 * common fake-BTS trait. Rebuilt on getAllCellInfo (the old
 * getNeighboringCellInfo() was removed in API 29).
 */
class EmptyNeighborListHeuristic : Heuristic {
    override val id = "empty_neighbor_list"

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null
        if (serving.rat.generation < 4) return null
        if (ctx.deviceMoving) return null

        val others = ctx.visible.count { it.key != serving.key }
        if (others > 0) return null

        return HeuristicResult(
            id = id,
            title = "Aucune cellule voisine",
            detail = "Cellule ${serving.rat.name} servante seule, sans voisine visible.",
            weight = HeuristicWeights.EMPTY_NEIGHBOR_LIST
        )
    }
}
