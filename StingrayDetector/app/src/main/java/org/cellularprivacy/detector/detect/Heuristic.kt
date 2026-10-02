package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.model.HeuristicResult

/**
 * Service-provider interface for a single detection rule. Each heuristic is
 * pure and independently testable: given a [DetectionContext] it returns a
 * [HeuristicResult] when it fires, or null when it has nothing to say.
 */
interface Heuristic {
    val id: String
    fun evaluate(ctx: DetectionContext): HeuristicResult?
}
