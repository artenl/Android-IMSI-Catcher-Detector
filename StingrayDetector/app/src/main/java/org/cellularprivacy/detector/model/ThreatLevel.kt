package org.cellularprivacy.detector.model

/**
 * Overall assessment shown to the user. Ordered by severity so levels can be
 * compared with >/<. Deliberately coarse: the app cannot be certain, so it
 * speaks in likelihoods, not verdicts.
 */
enum class ThreatLevel {
    NORMAL,      // nothing noteworthy
    INFO,        // a single weak signal, worth logging
    SUSPICIOUS,  // several weak signals or one strong one
    HIGH;        // strong combined evidence of a fake cell

    companion object {
        /** Map an aggregate score to a level. Thresholds are tunable. */
        fun fromScore(score: Int): ThreatLevel = when {
            score <= 0 -> NORMAL
            score < 30 -> INFO
            score < 70 -> SUSPICIOUS
            else -> HIGH
        }
    }
}
