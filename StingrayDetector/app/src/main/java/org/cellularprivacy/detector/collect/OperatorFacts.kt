package org.cellularprivacy.detector.collect

/**
 * Facts about the active SIM, used by detection heuristics. Kept free of any
 * Android import so the detection graph stays unit-testable on a plain JVM.
 */
data class OperatorFacts(
    val simMcc: Int?,
    val simMnc: Int?,
    val isRoaming: Boolean
)
