package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult

/**
 * Fires when the serving cell advertises an operator (MCC/MNC) different from
 * the SIM's home operator while the device is not roaming. A catcher often
 * impersonates one PLMN but slips on the exact MCC/MNC.
 */
class OperatorMismatchHeuristic : Heuristic {
    override val id = "operator_mismatch"

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null
        val simMcc = ctx.operator.simMcc ?: return null
        val simMnc = ctx.operator.simMnc ?: return null
        val cellMcc = serving.mcc ?: return null
        val cellMnc = serving.mnc ?: return null
        if (ctx.operator.isRoaming) return null
        if (cellMcc == simMcc && cellMnc == simMnc) return null

        return HeuristicResult(
            id = id,
            title = "Opérateur incohérent",
            detail = "Cellule $cellMcc/$cellMnc vs SIM $simMcc/$simMnc, hors itinérance.",
            weight = HeuristicWeights.OPERATOR_MISMATCH
        )
    }
}
