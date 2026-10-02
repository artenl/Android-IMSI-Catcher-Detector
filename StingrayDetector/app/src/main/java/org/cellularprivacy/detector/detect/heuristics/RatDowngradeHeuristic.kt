package org.cellularprivacy.detector.detect.heuristics

import org.cellularprivacy.detector.detect.DetectionContext
import org.cellularprivacy.detector.detect.Heuristic
import org.cellularprivacy.detector.detect.HeuristicWeights
import org.cellularprivacy.detector.model.HeuristicResult
import org.cellularprivacy.detector.model.Rat

/**
 * Fires when the serving cell drops to 2G (GSM) shortly after a healthy 4G/5G
 * connection, while the device was not moving. This is the phone-side echo of
 * Rayhunter's "2G downgrade": a classic catcher move to strip encryption.
 *
 * In France (MCC 208) the 2G network is being switched off, so a GSM fallback
 * is far more anomalous and gets an extra weight.
 */
class RatDowngradeHeuristic : Heuristic {
    override val id = "rat_downgrade"

    private val lookbackMs = 30_000L

    override fun evaluate(ctx: DetectionContext): HeuristicResult? {
        val serving = ctx.serving ?: return null
        if (!serving.rat.isTwoG) return null
        if (ctx.deviceMoving) return null

        val now = serving.timestampMs
        val priorHighG = ctx.history.lastServingOfGeneration(
            minGeneration = 4, withinMs = lookbackMs, now = now
        ) ?: return null

        // Only suspicious if the prior 4G/5G signal was actually healthy;
        // a downgrade from a dying LTE edge cell is normal.
        val priorDbm = priorHighG.dbm ?: return null
        if (priorDbm < HeuristicWeights.HEALTHY_LTE_RSRP) return null

        val franceBoost = if (ctx.networkMcc == HeuristicWeights.FRANCE_MCC)
            HeuristicWeights.RAT_DOWNGRADE_FRANCE_BOOST else 0

        return HeuristicResult(
            id = id,
            title = "Rétrogradation vers la 2G",
            detail = "Passage ${priorHighG.rat.name} -> ${Rat.GSM.name} à l'arrêt, " +
                "signal 4G sain (${priorDbm} dBm) juste avant.",
            weight = HeuristicWeights.RAT_DOWNGRADE + franceBoost
        )
    }
}
