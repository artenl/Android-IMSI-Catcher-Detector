package org.cellularprivacy.detector.detect

import org.cellularprivacy.detector.collect.OperatorFacts
import org.cellularprivacy.detector.model.CellSnapshot

/**
 * Everything a heuristic may inspect for one evaluation. Built fresh from each
 * incoming batch by the [DetectionEngine].
 */
data class DetectionContext(
    /** The registered/serving cell, if one is present in the batch. */
    val serving: CellSnapshot?,
    /** All cells visible in this batch. */
    val visible: List<CellSnapshot>,
    /** Rolling history across batches. */
    val history: CellHistory,
    /** Facts about the active SIM. */
    val operator: OperatorFacts,
    /** Whether the device is currently moving (from the accelerometer). */
    val deviceMoving: Boolean
) {
    /** Network MCC as seen on the serving cell (208 = France). */
    val networkMcc: Int? get() = serving?.mcc
}
