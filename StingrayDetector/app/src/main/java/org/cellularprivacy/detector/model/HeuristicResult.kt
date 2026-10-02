package org.cellularprivacy.detector.model

/**
 * The output of one [org.cellularprivacy.detector.detect.Heuristic] for one
 * evaluation. [weight] is the score contribution if fully confident;
 * [confidence] in [0.0, 1.0] scales it and decays over time/distance so a
 * positive clears once the user leaves the area.
 */
data class HeuristicResult(
    val id: String,
    val title: String,
    val detail: String,
    val weight: Int,
    val confidence: Double = 1.0,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val score: Int get() = (weight * confidence).toInt()
}
