package org.cellularprivacy.detector.locate

import org.cellularprivacy.detector.util.Geo
import kotlin.math.pow

/** One geo-tagged measurement of a cell. */
data class Observation(
    val lat: Double,
    val lon: Double,
    val dbm: Int?,
    val timingAdvance: Int? = null
)

/** Estimated position of a cell, with a rough accuracy radius. */
data class CellEstimate(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Double,
    val samples: Int
)

/**
 * Estimates a cell's location from the user's own geo-tagged measurements -
 * no external database, no network. This is single-device self-localisation:
 * as the user moves around the cell, measurements accumulate and the estimate
 * converges, the same principle crowdsourced databases use.
 *
 * Each measurement is weighted by received signal power (linear mW from dBm):
 * stronger samples are closer to the antenna and pull the estimate toward it.
 * The accuracy radius is the signal-weighted mean distance of samples from the
 * estimate, floored so we never over-claim precision.
 */
object CellLocator {

    private const val MIN_ACCURACY_M = 50.0

    fun estimate(observations: List<Observation>): CellEstimate? {
        val pts = observations.filter { it.lat.isFinite() && it.lon.isFinite() }
        if (pts.isEmpty()) return null

        // Linear power weight; a missing dBm gets the weakest observed weight.
        val weights = pts.map { o -> o.dbm?.let { 10.0.pow(it / 10.0) } ?: 0.0 }
        val wSum = weights.sum().takeIf { it > 0.0 }

        val (lat, lon) = if (wSum == null) {
            // No usable signal values: plain centroid.
            pts.map { it.lat }.average() to pts.map { it.lon }.average()
        } else {
            var la = 0.0; var lo = 0.0
            for (i in pts.indices) { la += pts[i].lat * weights[i]; lo += pts[i].lon * weights[i] }
            (la / wSum) to (lo / wSum)
        }

        val dists = pts.map { Geo.haversine(lat, lon, it.lat, it.lon) }
        val accuracy = if (wSum == null) {
            dists.average()
        } else {
            var acc = 0.0
            for (i in pts.indices) acc += dists[i] * weights[i]
            acc / wSum
        }.coerceAtLeast(MIN_ACCURACY_M)

        return CellEstimate(lat, lon, accuracy, pts.size)
    }
}
