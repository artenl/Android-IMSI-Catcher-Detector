package org.cellularprivacy.detector.detect

/**
 * Tunable scoring weights, centralised so they can be adjusted (and later
 * moved to remote config or user preference) without touching heuristic logic.
 */
object HeuristicWeights {
    const val RAT_DOWNGRADE = 45
    const val RAT_DOWNGRADE_FRANCE_BOOST = 25   // 2G shutdown: a GSM fallback is far more suspicious
    const val UNKNOWN_STRONG_CELL = 30
    const val OPERATOR_MISMATCH = 40
    const val EMPTY_NEIGHBOR_LIST = 20
    const val SUSPICIOUS_PHYSICAL = 35
    const val SIGNAL_ANOMALY = 20

    const val FRANCE_MCC = 208

    // Thresholds
    const val SATURATED_DBM = -65        // stronger than this on a new cell is odd
    const val HEALTHY_LTE_RSRP = -100    // "we had good LTE" before a downgrade
    const val SIGNAL_ANOMALY_DELTA = 12  // dBm off baseline to flag
    const val CLOSE_TIMING_ADVANCE = 1   // TA ~0 => emitter within tens of metres
}
